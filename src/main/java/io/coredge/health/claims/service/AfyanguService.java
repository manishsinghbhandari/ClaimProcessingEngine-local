package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.coredge.health.claims.client.AfyanguClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AfyanguClaimsAudit;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.*;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.AfyanguClaimsDTO;
import io.coredge.health.claims.models.phccalculation.ClaimsAfyanguProjection;
import io.coredge.health.claims.models.redis.ClaimICDRelation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.GLOBAL_CAPITATION_CAPITATION;

@Slf4j
@Service
public class AfyanguService {

    @Autowired
    private AfyanguClient afyanguClient;

    @Autowired
    private AfyanguAuditRepository afyanguAuditRepository;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private ClaimICDRelationRepository claimICDRelationRepository;

    @Autowired
    private BenefitsMasterRepository benefitsRepository;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private ErpClaimsRepository erpClaimsRepository;

    @Value("${webhooks.enabled:false}")
    private Boolean isWebhooksEnabled;

    ObjectMapper objectMapper = new ObjectMapper();

    @Async("virtualThreadExecutor")
    public CompletableFuture<Void> sendWebhook(String claimId) {
        return CompletableFuture.runAsync(() -> {
            log.info("Sending webhook to Afyangu for claimId: {}", claimId);

            UUID claimUUId = UUID.fromString(claimId);

            try {
                InsuranceClaim insuranceClaim = claimsRepository.findByClaimId(claimUUId)
                        .flatMap(claims -> claims.stream().findFirst())
                        .orElseThrow(() -> new NoSuchElementException("Claim not found for claimId: " + claimId));

                List<String> codes = lineItemRepository.findDistinctCodesByClaimId(claimId)
                        .orElseThrow(() -> new NoSuchElementException("Claim items not found for claimId: " + claimId));

                if (insuranceClaim.getUse().trim().equals(Constants.Use.CLAIM) &&
                        benefitsRepository.existsByInterventionCodeInAndProviderPaymentMechanism(codes, GLOBAL_CAPITATION_CAPITATION)) {
                    log.info("Claim is a global capitation claim, skipping webhook for claimId: {}", claimId);
                    return;
                }

                AfyanguClaimsDTO.ERP erpObj = (insuranceClaim.getStatus() == ExtensionStateCodeEnum.PAYMENT_COMPLETED
                        || insuranceClaim.getStatus() == ExtensionStateCodeEnum.PAYMENT_DECLINED)
                        ? erpClaimsRepository.findByClaimNo(String.valueOf(insuranceClaim.getClaimId()))
                        .map(this::createErpFromClaim)
                        .orElseGet(AfyanguClaimsDTO.ERP::new)
                        : new AfyanguClaimsDTO.ERP();

                List<String> icdCodes = Optional.ofNullable(insuranceClaim.getIcdCodes())
                        .filter(StringUtils::isNotEmpty)
                        .map(codesStr -> Arrays.asList(codesStr.split(" \\| ")))
                        .orElseGet(() -> claimICDRelationRepository.findDistinctIcdCodesByClaimId(claimId)
                                .orElseThrow(() -> new NoSuchElementException("Claim ICD codes not found for claimId: " + claimId)));

                AfyanguClaimsDTO afyanguClaimsDTO = fromClaim(insuranceClaim, codes, icdCodes, erpObj);
                String bodyJson = new Gson().toJson(afyanguClaimsDTO);

                log.info("Claim with status: {} and Afyangu body: {}", insuranceClaim.getStatus(), bodyJson);

                AfyanguClaimsAudit claimsAudit = saveAudit(bodyJson, insuranceClaim.getClaimId(), insuranceClaim.getStatus().toString(), insuranceClaim.getUse().trim());

                // Use WebClient for Non-Blocking Call
                afyanguClient.postClaims(afyanguClaimsDTO)
                        .doOnSuccess(response -> {
                            try {
                                processResponse(response, claimsAudit, claimId);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .doOnError(error -> log.warn("Webhook failed: claimId : {} {} ", claimId, error.getMessage()))
                        .subscribe(); // Executes in background

            } catch (Exception e) {
                log.warn("Error occurred while sending webhook: {}", e.getMessage(), e);
            }
        });
    }



    private void processResponse(ResponseEntity<String> response, AfyanguClaimsAudit claimsAudit, String claimId) throws JsonProcessingException {
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String reference = jsonNode.path("reference").asText();
            log.info("Webhook sent successfully to Afyangu for claimId: {} received ReferenceNo: {}", claimId, reference);

            claimsAudit.setStatus("SUCCESS");
            claimsAudit.setReferenceNo(reference);
        } else {
            log.warn("Webhook failed to send to Afyangu for claimId: {}", claimId);
            claimsAudit.setStatus("FAILED");
        }
        claimsAudit.setResponseBody(response.getBody());
        updateAudit(claimsAudit);
    }

    private AfyanguClaimsAudit saveAudit(String bodyJson, UUID claimId, String claimCurrentStatus, String use) {
        log.info("Saving Afyangu audit for claimId: {}", claimId);
        try {
            AfyanguClaimsAudit audit = afyanguAuditRepository.
                    findByClaimIdAndStatus(claimId.toString(), Constants.WEBHOOK_STATUS.AFYANGU)
                    .filter(ec -> ec.getClaimStatus().equals(ExtensionStateCodeEnum.QUEUED.name()))
                    .map(existingAudit -> {
                        existingAudit.setStatus("PENDING");
                        existingAudit.setRequestBody(bodyJson);
                        existingAudit.setClaimStatus(claimCurrentStatus);
                        return existingAudit;
                    })
                    .orElseGet(() -> AfyanguClaimsAudit.builder()
                            .claimId(claimId.toString())
                            .status("PENDING")
                            .claimStatus(claimCurrentStatus)
                            .requestBody(bodyJson)
                            .identifierType(Constants.WEBHOOK_STATUS.AFYANGU)
                            .use(use)
                            .build());

            return afyanguAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Error occurred while saving data: {}", e.getMessage(), e);
            throw new RuntimeException("Error occurred while saving data", e);
        }
    }


    private void updateAudit(AfyanguClaimsAudit audit) {
        log.info("Updating Afyangu audit for claimId: {}", audit.getClaimId());
        try {
            afyanguAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Error occurred while updating data: {}", e.getMessage());
            throw new RuntimeException("Error occurred while updating data: " + e.getMessage());
        }
    }

    private AfyanguClaimsDTO fromClaim(InsuranceClaim iClaim, List<String> shaCodes, List<String> icdCodes, AfyanguClaimsDTO.ERP erpObj) {
        String organizationId = iClaim.getProvider().getMflCode() != null ? iClaim.getProvider().getMflCode() : iClaim.getProvider().getFidCode();
        String patientId = iClaim.getPatient().getCrId();
        String patientSearchType = "shanumber";

        return AfyanguClaimsDTO.builder()
                .claimId(iClaim.getClaimId())
                .use(iClaim.getUse().trim())
                .claimType(iClaim.getClaimType())
                .organizationId(organizationId.startsWith("MFL-")
                        ? organizationId.replaceFirst("^MFL-", "")
                        : organizationId)
                .patientNumber(patientId)
                .patientSearchType(patientSearchType)
                .claimNet(iClaim.getClaimedTotal())
                .subType(iClaim.getAccessCode())
                .admissionStartDate(iClaim.getDateFrom().toString())
                .admissionEndDate(iClaim.getDateTo().toString())
                .createdDate(iClaim.getCreationDate().toString())
                .diagnosis(icdCodes)
                .priority("Normal")
                .stage(ExtensionStateCodeEnum.QUEUED.equals(iClaim.getStatus()) ? "CREATED" : iClaim.getStatus().toString())
                .intervention(shaCodes)
                .erp(erpObj)
                .build();

    }

    private AfyanguClaimsDTO fromClaimProjection(ClaimsAfyanguProjection claimsAfyanguProjection, List<String> shaCodes, List<String> icdCodes, AfyanguClaimsDTO.ERP erpObj) {
        String organizationId = claimsAfyanguProjection.getMflCode() != null ? claimsAfyanguProjection.getMflCode() : claimsAfyanguProjection.getFidCode();
        String patientId = claimsAfyanguProjection.getPatientId();
        String patientSearchType = "shanumber";

        return AfyanguClaimsDTO.builder()
                .claimId(claimsAfyanguProjection.getClaimId())
                .use(claimsAfyanguProjection.getUse())
                .claimType(claimsAfyanguProjection.getClaimType())
                .organizationId(organizationId.startsWith("MFL-")
                        ? organizationId.replaceFirst("^MFL-", "")
                        : organizationId)
                .patientNumber(patientId)
                .patientSearchType(patientSearchType)
                .claimNet(claimsAfyanguProjection.getTotalAmount())
                .subType(claimsAfyanguProjection.getAccessCode())
                .admissionStartDate(claimsAfyanguProjection.getDateFrom().toString())
                .admissionEndDate(claimsAfyanguProjection.getDateTo().toString())
                .createdDate(claimsAfyanguProjection.getCreationDate().toString())
                .diagnosis(icdCodes)
                .priority("Normal")
                .stage(ExtensionStateCodeEnum.QUEUED.equals(claimsAfyanguProjection.getStatus()) ? "CREATED" : claimsAfyanguProjection.getStatus().toString())
                .intervention(shaCodes)
                .erp(erpObj)
                .build();

    }

    @Async("virtualThreadExecutor")
    public void sendWebhookAsyncJob() {
        log.info("Starting webhook job for Afyangu...");

        int batchSize = 5000; // Batch size for fetching claims
        int page = 0;
        AtomicBoolean hasMoreData = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        while (hasMoreData.get()) {
            final int currentPage = page;

            // Process each page asynchronously
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                log.info("Processing batch page: {}", currentPage);

                List<ExtensionStateCodeEnum> statuses = List.of(
                        ExtensionStateCodeEnum.APPROVED,
                        ExtensionStateCodeEnum.REJECTED,
                        ExtensionStateCodeEnum.SENT_BACK,
                        ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING,
                        ExtensionStateCodeEnum.PAYMENT_COMPLETED,
                        ExtensionStateCodeEnum.PAYMENT_DECLINED,
                        ExtensionStateCodeEnum.CANCELED
                );

                Page<ClaimsAfyanguProjection> claimsPage = claimsRepository.findAllNonPHCClaimsByStatusOptimized(
                        statuses, PageRequest.of(currentPage, batchSize, Sort.by(Sort.Direction.ASC, "claimNumber"))
                );

                List<ClaimsAfyanguProjection> insuranceClaims = claimsPage.getContent();
                hasMoreData.set(!claimsPage.isLast());

                if (insuranceClaims.isEmpty()) {
                    log.info("No more claims found, stopping batch processing.");
                    hasMoreData.set(false);
                    return;
                }

                List<String> claimIds = insuranceClaims.stream()
                        .map(claim -> claim.getClaimId().toString())
                        .collect(Collectors.toList());

                List<String> claimNos = insuranceClaims.stream()
                        .filter(i -> ExtensionStateCodeEnum.PAYMENT_COMPLETED.equals(i.getStatus()))
                        .map(claim -> claim.getClaimId().toString())
                        .toList();

                Map<String, AfyanguClaimsDTO.ERP> claimErpMap = getClaimErpMap(claimNos);
                Set<String> existingClaimIds = new HashSet<>(afyanguAuditRepository.findExistingClaimIds(claimIds));
                Map<String, List<String>> claimLineItemsMap = getClaimLineItemsMap(claimIds);
                Map<String, List<String>> claimIcdCodesMap = getClaimIcdCodesMap(claimIds);

                insuranceClaims.parallelStream().forEach(insuranceClaim -> {
                    String claimId = insuranceClaim.getClaimId().toString();

                    if (existingClaimIds.contains(claimId)) {
                        log.info("Skipping already existing claim: {}", claimId);
                        return;
                    }

                    List<String> codes = claimLineItemsMap.getOrDefault(claimId, Collections.emptyList());
                    if (codes.isEmpty()) {
                        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
                        codes = claim.getItem().stream()
                                .flatMap(item -> item.getProductOrService().getCoding().stream())
                                .map(Coding::getCode)
                                .collect(Collectors.toList());
                    }

                    List<String> icdCodes = StringUtils.isEmpty(insuranceClaim.getIcdCodes())
                            ? claimIcdCodesMap.getOrDefault(claimId, Collections.emptyList())
                            : Arrays.asList(insuranceClaim.getIcdCodes().split("\\|"));

                    AfyanguClaimsDTO.ERP erpObj = claimErpMap.getOrDefault(insuranceClaim.getClaimNo().toString(), new AfyanguClaimsDTO.ERP());
                    AfyanguClaimsDTO afyanguClaimsDTO = fromClaimProjection(insuranceClaim, codes, icdCodes, erpObj);
                    String bodyJson = new Gson().toJson(afyanguClaimsDTO);

                    log.info("Processing claim {} with status: {}", insuranceClaim.getClaimNo(), insuranceClaim.getStatus());

                    AfyanguClaimsAudit claimsAudit = saveAudit(bodyJson, insuranceClaim.getClaimId(), insuranceClaim.getStatus().toString(),insuranceClaim.getUse().trim() );

                    afyanguClient.postClaims(afyanguClaimsDTO)
                            .doOnSuccess(response -> {
                                try {
                                    processResponse(response, claimsAudit, claimId);
                                } catch (JsonProcessingException e) {
                                    log.warn("JSON Processing Error for claim {}: {}", claimId, e.getMessage());
                                }
                            })
                            .doOnError(error -> log.warn("Error posting claim {}: {}", claimId, error.getMessage()))
                            .subscribe();
                });
            });

            futures.add(future);
            page++;
        }

        // Wait for all async tasks to complete before finishing
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> {
                    log.warn("Error occurred during webhook processing: {}", ex.getMessage());
                    return null;
                })
                .join();

        log.info("Webhook job completed.");
    }


    private Map<String, AfyanguClaimsDTO.ERP> getClaimErpMap(List<String> claimNos) {
        List<ErpClaims> erpResponse = erpClaimsRepository.findAllByClaimNoIn(claimNos);
        return erpResponse.stream()
                .collect(Collectors.toMap(ErpClaims::getClaimNo, this::createErpFromClaim));
    }

    private AfyanguClaimsDTO.ERP createErpFromClaim(ErpClaims erpClaims) {
        AfyanguClaimsDTO.ERP dto = new AfyanguClaimsDTO.ERP();
        dto.setClaimAmount(erpClaims.getClaimAmount());
        dto.setPayableAmount(erpClaims.getAmount());
        dto.setPaymentDate(erpClaims.getDate().toString());
        dto.setRejectionReason(erpClaims.getRejection_reason() != null ? erpClaims.getRejection_reason() : "");
        return dto;
    }

    public Map<String, List<String>> getClaimLineItemsMap(List<String> claimIds) {
        List<Object[]> results = lineItemRepository.findDistinctCodesByClaimIds(claimIds);
        return results.stream()
                .collect(Collectors.groupingBy(
                        obj -> (String) obj[0],  // claimId
                        Collectors.mapping(obj -> (String) obj[1], Collectors.toList()) // list of codes
                ));
    }

    public Map<String, List<String>> getClaimIcdCodesMap(List<String> claimIds) {
        List<ClaimICDRelation> results = claimICDRelationRepository.findDistinctIcdCodesByClaimIds(claimIds);

        return results.stream()
                .filter(relation -> relation.getDiagnosis() != null)  // Filter out null diagnosis lists
                .collect(Collectors.toMap(
                        ClaimICDRelation::getClaimId,  // Extract claimId
                        relation -> relation.getDiagnosis().stream()
                                .map(ClaimICDRelation.Diagnosis::getIcdCode)  // Extract ICD codes
                                .filter(Objects::nonNull)  // Filter out null ICD codes
                                .collect(Collectors.toList()),
                        (existing, replacement) -> {  // Merge function (if duplicate claimIds exist)
                            existing.addAll(replacement);
                            return existing;
                        }
                ));
    }

}
