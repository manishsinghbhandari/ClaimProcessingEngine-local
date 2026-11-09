package io.coredge.health.claims.service.cpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.coredge.health.claims.client.CppClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AfyanguClaimsAudit;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.AfyanguAuditRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.mapper.ProvidersToCPPMapper;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.cpp.RequestCppClaim;
import io.coredge.health.claims.models.cpp.RequestCppPreAuth;
import io.coredge.health.claims.models.cpp.RequestCppProviders;
import io.coredge.health.claims.service.MemberService;
import io.coredge.health.claims.utils.ClaimsValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CppService {

    @Value("${cpp.data.enable:false}")
    private Boolean cppDataEnable;

    @Value("${cpp.payment.enable:false}")
    private boolean cppPayment;

    private final CppClient cppClient;
    private final AfyanguAuditRepository afyanguAuditRepository;
    private final InsuranceClaimsRepository claimsRepository;
    private final ObjectMapper objectMapper;
    private final ClaimPaymentHelper claimPaymentHelper;
    private final LineItemRepository lineItemRepository;
    private final MemberService memberService;
    private final ClaimsValidator claimsValidator;

    public CppService(CppClient cppClient, AfyanguAuditRepository afyanguAuditRepository, InsuranceClaimsRepository claimsRepository, ObjectMapper objectMapper, ClaimPaymentHelper claimPaymentHelper, LineItemRepository lineItemRepository, MemberService memberService, ClaimsValidator claimsValidator) {
        this.cppClient = cppClient;
        this.afyanguAuditRepository = afyanguAuditRepository;
        this.claimsRepository = claimsRepository;
        this.objectMapper = objectMapper;
        this.claimPaymentHelper = claimPaymentHelper;
        this.lineItemRepository = lineItemRepository;
        this.memberService = memberService;
        this.claimsValidator = claimsValidator;
    }

    @Async("virtualThreadExecutor")
    public void sendWebhook(String claimId, boolean cppBackProcess) {
        CompletableFuture.runAsync(() -> {
            log.info("Sending webhook to Cpp for claimId: {}", claimId);
            UUID claimUUId = UUID.fromString(claimId);

            try {
                InsuranceClaim insuranceClaim = claimsRepository.findByClaimId(claimUUId)
                        .flatMap(claims -> claims.stream().findFirst())
                        .orElseThrow(() -> new NoSuchElementException("Claim not found for claimId: " + claimId));

                if (cppBackProcess && insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.APPROVED)){
                    claimsValidator.validateClaimStatusBeforeUpdate(insuranceClaim.getStatus() , ExtensionStateCodeEnum.QUEUED , insuranceClaim.getUse() , claimId);
                    insuranceClaim.setStatus(ExtensionStateCodeEnum.QUEUED);
                }
                log.info("CPP InsuranceClaim Status: {}, ClaimId: {}", insuranceClaim.getStatus(), insuranceClaim.getClaimId());
                boolean isUpdate = !insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.QUEUED);

                if (cppPayment && insuranceClaim.getUse().trim().equals(Constants.Use.CLAIM) &&
                        !insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.QUEUED)) {
                   return;
                }
                if (insuranceClaim.getUse().trim().equals(Constants.Use.CLAIM) && insuranceClaim.isPHCClaim()) {
                    log.warn("Claim is a global capitation claim, skipping webhook for claimId: {}", claimId);
                    return;
                }
                // Added as SENT_FOR_PAYMENT_PROCESSING status not needed to push in CPP. 
                if (insuranceClaim.getUse().trim().equals(Constants.Use.CLAIM)
                        && insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING)) {
                    log.warn("Claim with status 'SENT_FOR_PAYMENT_PROCESSING' restricted temp, Skipping webhook for claimId: {}", claimId);
                    return;
                }


                RequestCppObject requestClientCpp = getCppRequest(insuranceClaim, isUpdate , cppBackProcess);
                String bodyJson = new Gson().toJson(requestClientCpp.request());
                log.info("CPP Claim with status: {} and CPP body: {}", insuranceClaim.getStatus(), bodyJson);
                AfyanguClaimsAudit claimsAudit = saveAudit(bodyJson, insuranceClaim.getClaimId(), insuranceClaim.getStatus().name(), insuranceClaim.getUse().trim());

                // Use WebClient for Non-Blocking Call
                cppClient.postClaims(requestClientCpp)
                        .doOnSuccess(response -> {
                            try {
                                processResponse(response, claimsAudit, claimId);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .doOnError(error -> log.warn("CPP Request failed: claimId : {} Status: {} ,ExpMsg, {} ", claimId, insuranceClaim.getStatus(), error.getMessage()))
                        .subscribe(); // Executes in background
            } catch (Exception e) {
                log.warn("CPP Error occurred while sending req: {}", e.getMessage(), e);
            }
        });
    }


    private void processResponse(ResponseEntity<String> response, AfyanguClaimsAudit claimsAudit, String claimId) throws JsonProcessingException {
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String reference = jsonNode.path("claimReferenceId").asText();
            log.info("CPP Req sent successfully to CPP for claimId: {} received ReferenceNo: {}", claimId, reference);
            claimsAudit.setStatus("SUCCESS");
            claimsAudit.setReferenceNo(reference);
        } else {
            log.warn("CPP Request failed to send to CPP for claimId: {}", claimId);
            claimsAudit.setStatus("FAILED");
        }
        claimsAudit.setResponseBody(response.getBody());
        updateAudit(claimsAudit);
    }

    private AfyanguClaimsAudit saveAudit(String bodyJson, UUID claimId, String claimCurrentStatus, String use) {
        return AfyanguClaimsAudit.builder()
                .claimId(String.valueOf(claimId))
                .status("PENDING")
                .claimStatus(claimCurrentStatus)
                .requestBody(bodyJson)
                .use(use)
                .identifierType(Constants.WEBHOOK_STATUS.CPP)
                .build();
    }


    private void updateAudit(AfyanguClaimsAudit audit) {
        log.info("Updating CPP audit for claimId: {}", audit.getClaimId());
        try {
            afyanguAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Error occurred while updating data: {}", e.getMessage());
            throw new RuntimeException("Error occurred while updating data: " + e.getMessage());
        }
    }

    private RequestCppObject getCppRequest(InsuranceClaim iClaim, boolean isUpdate ,boolean claimReProcess) {
        log.info("CPP request, Claim:{}.  status:{}, use:{}", iClaim.getClaimId(), iClaim.getStatus().name(), iClaim.getUse());
        if (iClaim.getUse().trim().equals(Constants.Use.PREAUTHORIZATION)) {
            return forPreAuth(iClaim, isUpdate);
        } else {
            return forClaim(iClaim, isUpdate, claimReProcess);
        }
    }

    private RequestCppObject forPreAuth(InsuranceClaim iClaim, boolean isUpdate) {
        String organizationId = iClaim.getProvider().getFidCode();
        String beneficiaryShaId = iClaim.getPatient().getShaNumber();
        String beneficiaryCrNumber = iClaim.getPatient().getCrId();

        RequestCppPreAuth.RequestCppPreAuthBuilder builder = RequestCppPreAuth.builder()
                .claimId(iClaim.getExternalId())
                .claimType(Constants.CppClaimType.PRE_AUTH)
                .fid(organizationId)
                .providerName(iClaim.getProvider().getName())
                .beneficiaryShaId(beneficiaryShaId)
                .beneficiaryCrNumber(beneficiaryCrNumber)
                .beneficiaryName(iClaim.getPatient().getName())
                .primaryContributorNumber(beneficiaryCrNumber)
                .claimAmount(iClaim.getClaimedTotal())
                .claimDate(iClaim.getCreationDate().toString());

        if (iClaim.getUse().trim().equals(Constants.Use.PREAUTHORIZATION)) {
            if (isUpdate) {
                AfyanguClaimsAudit audit = afyanguAuditRepository
                        .findByClaimIdAndClaimStatusAndIdentifierType(iClaim.getExternalId(),
                                ExtensionStateCodeEnum.QUEUED.name(), Constants.WEBHOOK_STATUS.CPP)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("CPP Record not found for claim ID " + iClaim.getExternalId() + " with status QUEUED"));
                builder
                        .preAuthReferenceId(audit.getReferenceNo())
                        .preAuthApprovedAmount(iClaim.getClaimedTotal())
                        .status(iClaim.getStatus().name());
            }
        }
        return new RequestCppObject(builder.build(), Constants.CppClaimType.PRE_AUTH, iClaim.getExternalId(), isUpdate);
    }


    private RequestCppObject forClaim(InsuranceClaim iClaim, boolean isUpdate, boolean claimReProcess) {
        String organizationId = iClaim.getProvider().getFidCode();
        String beneficiaryShaId = iClaim.getPatient().getShaNumber();
        String beneficiaryCrNumber = iClaim.getPatient().getCrId();

        String inVoiceNumber;
        Claim claim = claimPaymentHelper.getClaimObject(iClaim.getExternalId());

        ClaimPaymentHelper.InvoiceDetails invoiceDetails = claimPaymentHelper.getInvoiceDetails(claim);

        // Intervention, PreAuth id
        InterventionCodeAndPreAuthId interventionCodeAndPreAuthId = getInterventionCodeAndPreAuthId(iClaim.getExternalId());
        String preAuthSequenceNumber = interventionCodeAndPreAuthId.preAuthId;
        if (StringUtils.isEmpty(preAuthSequenceNumber)) {
            preAuthSequenceNumber = getRelatedPreAuthIds(claim);
        }

        RequestCppClaim.RequestCppClaimBuilder builder = RequestCppClaim.builder()
                .claimId(iClaim.getExternalId())
                .claimType(Constants.CppClaimType.SUBMISSION)
                .fid(organizationId)
                .providerName(iClaim.getProvider().getName())
                .beneficiaryShaId(beneficiaryShaId)
                .beneficiaryCrNumber(beneficiaryCrNumber)
                .beneficiaryName(iClaim.getPatient().getName())
                .primaryContributorNumber(beneficiaryCrNumber)
                .claimAmount(iClaim.getClaimedTotal())
                .claimStatus(iClaim.getStatus().name())
                .claimDate(iClaim.getCreationDate().toString());


        if (isUpdate) {
            builder
                    .serviceDate(iClaim.getDateFrom().toString())
                    .county(iClaim.getProvider().getCounty())
                    .ownershipType(iClaim.getProvider().getFacilityAgent())
                    .invoiceNumber(invoiceDetails.invoiceNumber())
                    .serviceCode(interventionCodeAndPreAuthId.interventionCode())
                    .approvedClaimAmount(iClaim.getClaimedTotal())
                    .claimReferenceId(getReferenceId(iClaim.getExternalId()));
        } else {
            if (!claimReProcess){
                builder.preAuthSequenceNumber(preAuthSequenceNumber);
            }

        }
        return new RequestCppObject(builder.build(), Constants.CppClaimType.SUBMISSION, iClaim.getExternalId(), isUpdate);
    }

    private String getReferenceId(String claimId) {
        AfyanguClaimsAudit audit = afyanguAuditRepository
                .findByClaimIdAndClaimStatusAndIdentifierType(claimId, ExtensionStateCodeEnum.QUEUED.name(), Constants.WEBHOOK_STATUS.CPP).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CPP Record found for claim ID " + claimId + " with status QUEUED"));
        return audit.getReferenceNo();
    }

    private InterventionCodeAndPreAuthId getInterventionCodeAndPreAuthId(String claimID) {
        log.info("Fetching CPP request Intervention Code and Pre Auth Id for Claim {}", claimID);
        List<ClaimHistoryDTO> passedItems;
        try {
            passedItems = fetchClaimItems(claimID);

            String interventionCodes = passedItems.stream()
                    .map(ClaimHistoryDTO::getInterventionCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(","));

            String preAuthId = passedItems.stream()
                    .map(ClaimHistoryDTO::getPreAuthId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            return new InterventionCodeAndPreAuthId(interventionCodes, preAuthId);
        } catch (Exception e) {
            log.warn("Exception in the getInterventionCodeAndPreAuthId : exp {}", e.getMessage());
        }
        return new InterventionCodeAndPreAuthId("", "");
    }


    public String getRelatedPreAuthIds(Claim claim) {
        if (claim.getUse().equals(Claim.Use.CLAIM) && claim.hasRelated()) {
            return claim.getRelated().stream()
                    .map(Claim.RelatedClaimComponent::getClaim)
                    .map(ref -> Optional.ofNullable(ref.getId())
                            .filter(id -> !id.isBlank())
                            .orElseGet(() -> ref.getIdentifier() != null
                                    ? ref.getIdentifier().getValue()
                                    : null))
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }



    public List<ClaimHistoryDTO> fetchClaimItems(String claimId) {
        Optional<List<ClaimLineItem>> lineItems = lineItemRepository.findByClaimId(UUID.fromString(claimId));
        //Fetch the interventionName for the interventionCode
        return lineItems.map(items -> items.stream().map(memberService::getHistoryResponse).toList()).orElseThrow(DataNotFoundException::new);
    }


    // To Push the providers to CPP
    @Async("virtualThreadExecutor")
    public void pushToCpp(Provider provider) {

        if (!cppDataEnable) {
            log.info("Pushing to cpp providers is disabled,  skipping flow: providerID{}", provider.getFidCode());
        }
        if (provider == null) {
            log.info("No providers data to Push CPP");
            return;
        }
        // Create request Body for CPP
        RequestCppProviders request = ProvidersToCPPMapper.convertFromProvidersToCPP(provider);
        // Use WebClient for Non-Blocking Call
        cppClient.postProvides(request)
                .doOnSuccess(response -> {
                    log.info("Providers Pushed to CPP: {}", response);
                })
                .doOnError(error -> log.warn("CPP Request failed: claimId : {} ,ExpMsg, {} ", request.getFrCode(), error.getMessage()))
                .subscribe(); // Executes in background

    }

    public record RequestCppObject(Object request, String claimType, String claimId, Boolean isUpdate) {
    }

    public record InterventionCodeAndPreAuthId(String interventionCode, String preAuthId) {
    }
}
