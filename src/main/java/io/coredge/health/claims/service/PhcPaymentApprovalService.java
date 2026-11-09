package io.coredge.health.claims.service;

import io.coredge.health.claims.component.ClientsComponent;
import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import io.coredge.health.claims.db.entity.PhcProcessingCursor;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.repositories.PhcBatchPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcClaimPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcProcessingCursorRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.AdminLevelOneRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.AdminLevelPopulationRepository;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.eclaims.InterventionCodeRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.dto.phc.*;
import io.coredge.health.claims.service.PhcBackgroundProcessingService;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.models.eclaims.CppPaymentRequest;
import io.coredge.health.claims.models.eclaims.ERPResponseModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.context.HealthIdContextHolder;

import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.utils.FhirHelper.convertToStringFromDateTime;

/**
 * PHC Payment Approval Service
 * Handles approval workflow for PHC payments:
 * - County batch payment approval
 * - Facility payment approval
 * - ERP integration for approved payments
 * - Payment status tracking and completion
 */
@Data
@Slf4j
@Service
@RequiredArgsConstructor
public class PhcPaymentApprovalService {

    private final PhcBatchPaymentRepository phcBatchPaymentRepository;
    private final PhcClaimPaymentRepository phcClaimPaymentRepository;
    private final PhcProcessingCursorRepository cursorRepository;
    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final AdminLevelOneRepository adminLevelOneRepository;
    private final PhcBackgroundProcessingService phcBackgroundProcessingService;
    private final SendClaimForPaymentHandler sendClaimForPaymentHandler;
    private final ProviderRepository providerRepository;
    private final LineItemRepository lineItemRepository;
    private final ClientsComponent clientsComponent;
    private final AdminLevelPopulationRepository adminLevelPopulationRepository;
    private final PagedResourcesAssembler<PhcBatchPaymentDto> pagedResourcesAssembler;

    @Value("${erp.claim.reference.link}")
    private String erpClaimReferenceLink;

    @Value("${cpp.payment.enable:false}")
    private boolean cppPaymentEnabled;

    @Value("${phc.erp.cutoff.date:2024-11-07}")
    private String phcErpCutoffDateStr;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter MON_YYYY_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH);
    private static final String YYYYMM_PATTERN = "\\d{6}";


    /**
     * Get paginated list of county batch payments with filters
     */
    public Page<PhcBatchPaymentDto> getBatchPayments(String status, LocalDate startDate, LocalDate endDate, String countyName, Pageable pageable) {
        List<PhcBatchPayment> allBatchPayments = phcBatchPaymentRepository.findAll();
        List<PhcBatchPayment> filteredPayments = filterBatchPayments(allBatchPayments, status, startDate, endDate , countyName);

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredPayments.size());
        List<PhcBatchPayment> pageContent = filteredPayments.subList(start, end);

        List<PhcBatchPaymentDto> dtos = pageContent.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, filteredPayments.size());
    }

    public PagedModel<EntityModel<PhcBatchPaymentDto>> getBatchPaymentsFiltered(PhcBatchPayment.Status status, LocalDate startDate, LocalDate endDate, String countyName, Pageable pageable) {
        Optional<Page<PhcBatchPayment>> filteredPayments = phcBatchPaymentRepository.findPaymentsOptionalParams(status, startDate, endDate, countyName, pageable);

        Page<PhcBatchPaymentDto> responses = filteredPayments.filter(Page::hasContent).map(page -> page.map(this::convertToDto)).orElseThrow(DataNotFoundException::new);

        return pagedResourcesAssembler.toModel(responses);
    }


    private List<PhcBatchPayment> filterBatchPayments(List<PhcBatchPayment> batchPayments, String status, LocalDate startDate, LocalDate endDate , String countyName) {

        PhcBatchPayment.Status statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = PhcBatchPayment.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter provided: {}", status);
            }
        }

        PhcBatchPayment.Status finalStatusEnum = statusEnum;

        return batchPayments.stream()
                .filter(bp -> (finalStatusEnum == null || bp.getStatus() == finalStatusEnum) &&
                        (startDate == null || !bp.getDate().isBefore(startDate)) &&
                        (endDate == null || !bp.getDate().isAfter(endDate)))
                .filter(bp -> (StringUtils.isBlank(countyName) || StringUtils.equalsIgnoreCase(bp.getAdminLevelPopulation().getAdminLevelEntity().getName() , countyName)))
                .collect(Collectors.toList());
    }

    /**
     * Get specific batch payment details
     */
    public PhcBatchPaymentDto getBatchPaymentDetails(Long batchPaymentId) {
        PhcBatchPayment batchPayment = findBatchPaymentById(batchPaymentId);
        return convertToDto(batchPayment);
    }

    /**
     * Get facility payments for a specific batch payment
     */
    public Page<PhcClaimPaymentDto> getFacilityPayments(Long batchPaymentId, String providerName, String mflCode, Pageable pageable) {
        PhcBatchPayment batchPayment = findBatchPaymentById(batchPaymentId);
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batchPayment);

        // Apply additional filters if needed
        List<PhcClaimPayment> filteredPayments = filterFacilityPayments(facilityPayments, providerName, mflCode);

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredPayments.size());
        List<PhcClaimPayment> pageContent = filteredPayments.subList(start, end);

        List<PhcClaimPaymentDto> dtos = pageContent.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, filteredPayments.size());
    }

    private List<PhcClaimPayment> filterFacilityPayments(List<PhcClaimPayment> facilityPayments, String providerName, String mflCode) {
        return facilityPayments.stream()
                .filter(fp -> matchesProviderNameFilter(fp, providerName))
                .collect(Collectors.toList());
    }

    private boolean matchesProviderNameFilter(PhcClaimPayment facilityPayment, String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return true;
        }

        if (facilityPayment.getProviderId() == null) {
            return false;
        }

        Optional<Provider> providerOpt = providerRepository.findById(facilityPayment.getProviderId());
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();
            String actualProviderName = provider.getName();
            if (actualProviderName != null) {
                return actualProviderName.toLowerCase().contains(providerName.toLowerCase());
            }
        }

        return false;
    }

    /**
     * Get specific facility payment details
     */
    public PhcClaimPaymentDto getFacilityPaymentDetails(Long facilityPaymentId) {
        PhcClaimPayment facilityPayment = findFacilityPaymentById(facilityPaymentId);
        return convertToDto(facilityPayment);
    }


    /**
     * Send approved batch payment to ERP/CPP
     */
    public String sendBatchPaymentToErp(Long batchPaymentId) {
        log.info("Starting ERP submission for batch payment: {}", batchPaymentId);

        try {
            PhcBatchPayment batchPayment = validateBatchForErpSubmission(batchPaymentId);
            List<PhcClaimPayment> approvedPayments = getApprovedFacilityPayments(batchPayment);

            submitFacilityPaymentsToErp(approvedPayments);
            updateBatchStatusAfterErpSubmission(batchPayment);

            String message = String.format("Successfully sent batch payment %d to ERP with %d facility payments",
                    batchPaymentId, approvedPayments.size());
            log.info(message);

            return message;

        } catch (BadRequestException ex) {
            log.error("Validation error sending batch payment {} to ERP: {}", batchPaymentId, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            String errorMsg = "Failed to send batch payment to ERP: " + e.getMessage();
            log.error("Error sending batch payment {} to ERP", batchPaymentId);
            throw new BadRequestException(errorMsg);
        }
    }


    /**
     * Send facility payment to ERP
     * Used internally and by healer scheduler to retry failed submissions
     */
    public String sendFacilityPaymentToErp(PhcClaimPayment facilityPayment) {
        try {
            LocalDate serviceDate = facilityPayment.getBatchPreparationDate();
            LocalDate cutoffDate = LocalDate.parse(phcErpCutoffDateStr);

            // Determine payload type based on cutoff date and cpp flag
            boolean useCppPayload = cppPaymentEnabled && serviceDate != null && serviceDate.isAfter(cutoffDate);

            if (useCppPayload) {
                log.info("Sending PHC facility payment {} to CPP (Service Date: {}, Cutoff: {})",
                        facilityPayment.getId(), serviceDate, cutoffDate);
                return sendFacilityPaymentToCpp(facilityPayment);
            } else {
                log.info("Sending PHC facility payment {} to legacy ERP (Service Date: {}, Cutoff: {}, CPP Enabled: {})",
                        facilityPayment.getId(), serviceDate, cutoffDate, cppPaymentEnabled);
                return sendFacilityPaymentToLegacyErp(facilityPayment);
            }

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception e) {
            String errorMsg = "Failed to send facility payment to ERP: " + e.getMessage();
            log.error("Error sending facility payment {} to ERP: {}", facilityPayment.getId(), e);
            throw new BadRequestException(errorMsg, e);
        }
    }

    /**
     * Send facility payment using new CPP payload format directly to CPP endpoint
     */
    private String sendFacilityPaymentToCpp(PhcClaimPayment facilityPayment) {
        CppPaymentRequest cppRequest = createCppPayloadFromFacilityPayment(facilityPayment);
        logCppRequest(facilityPayment.getId(), cppRequest);

        String erpResponse = clientsComponent.processPhcClaim(cppRequest);
        if (erpResponse.contains("Error: ")) {
            erpResponse = erpResponse.substring(erpResponse.indexOf("Error: ") + 6);
        }
        return processCppErpResponse(facilityPayment, cppRequest, erpResponse);
    }

    /**
     * Send facility payment using legacy CreateClaimFundScheme payload format
     */
    private String sendFacilityPaymentToLegacyErp(PhcClaimPayment facilityPayment) {
        CreateClaimFundScheme claimRequest = createClaimFundSchemeFromFacilityPayment(facilityPayment);
        logErpRequest(facilityPayment.getId(), claimRequest);

        String erpResponse = clientsComponent.processERPClaim(claimRequest);
        if (erpResponse.contains("Error: ")) {
            erpResponse = erpResponse.substring(erpResponse.indexOf("Error: ") + 6);
        }
        return processErpResponse(facilityPayment, claimRequest, erpResponse);
    }

    /**
     * Common data holder for facility payment ERP/CPP requests
     */
    private record FacilityPaymentData(
            Provider provider,
            List<String> serviceCodeStrings,
            List<InterventionCodeRequest> interventionCodes,
            String invoiceNumber,
            String serviceDateStr
    ) {}

    /**
     * Extracts common data needed for both legacy and CPP payloads
     */
    private FacilityPaymentData extractFacilityPaymentData(PhcClaimPayment facilityPayment) {
        Provider provider = findProviderById(facilityPayment.getProviderId());

        // Get claims directly linked to this facility payment via foreign key
        List<InsuranceClaim> claims = insuranceClaimsRepository.findByPhcClaimPaymentsId(facilityPayment.getId())
                .orElse(Collections.emptyList());

        // Extract claim external IDs
        List<String> claimIds = claims.stream()
                .map(InsuranceClaim::getExternalId)
                .filter(Objects::nonNull)
                .toList();

        // Get distinct service codes from line items
        List<Object[]> serviceCodes = lineItemRepository.findDistinctCodesWithAmountByClaimIds(claimIds);

        // Extract service code strings
        List<String> serviceCodeStrings = serviceCodes.stream()
                .map(obj -> (String) obj[0])
                .toList();

        // Create list of InterventionCodeRequest
        List<InterventionCodeRequest> interventionCodes = serviceCodes.stream()
                .map(obj -> {
                    InterventionCodeRequest req = new InterventionCodeRequest();
                    req.setServiceCodeType((String) obj[0]);
                    req.setAmount((BigDecimal) obj[1]);
                    return req;
                })
                .toList();

        log.info("Found {} service codes for facility payment {} from {} linked claims",
                serviceCodes.size(), facilityPayment.getId(), claims.size());

        if (serviceCodeStrings.isEmpty()) {
            log.warn("No service codes found for facility payment: {}", facilityPayment.getId());
            throw new BadRequestException("Cannot send to ERP: No service codes found for facility payment " + facilityPayment.getId());
        }

        String serviceDateStr = facilityPayment.getBatchPreparationDate().atStartOfDay().toString();
        String invoiceNumber = generateInvoiceNumber();

        return new FacilityPaymentData(provider, serviceCodeStrings, interventionCodes, invoiceNumber, serviceDateStr);
    }

    /**
     * Creates ERP request from facility payment data using the legacy PHC approach
     */
    private CreateClaimFundScheme createClaimFundSchemeFromFacilityPayment(PhcClaimPayment facilityPayment) {
        FacilityPaymentData data = extractFacilityPaymentData(facilityPayment);

        List<CreateClaimFundScheme.FundScheme> fundSchemes = createPhcFundSchemes(facilityPayment.getApprovedAmount());
        String vendorCode = extractVendorCode(data.provider(), facilityPayment.getProviderId());

        CreateClaimFundScheme.CreateClaimFundSchemeBuilder builder = CreateClaimFundScheme.builder();
        builder.claimReferenceLink(erpClaimReferenceLink);
        builder.vendorCode(vendorCode);
        builder.vendorName(data.provider().getName());
        builder.beneficiaryName("SHA");
        builder.beneficiaryShaNo("SHA");
        builder.claimAmount(facilityPayment.getApprovedAmount());
        builder.claimNo(facilityPayment.getAggregatedClaimId().trim() + "_phc");
        builder.invoiceNumber(data.invoiceNumber());
        builder.serviceCode(data.serviceCodeStrings());
        builder.interventionCodes(data.interventionCodes());
        builder.fundScheme(fundSchemes);
        builder.serviceEndDate(Helper.getServiceDate(facilityPayment.getBatchPreparationDate().atStartOfDay()));

        return builder.build();

    }

    /**
     * Creates new CPP payload from facility payment data
     * Used when cpp.payment.enable=true AND serviceDate > cutoffDate
     */
    private CppPaymentRequest createCppPayloadFromFacilityPayment(PhcClaimPayment facilityPayment) {
        FacilityPaymentData data = extractFacilityPaymentData(facilityPayment);
        Provider provider = data.provider();
        String userId = HealthIdContextHolder.userId();
        String claimId = facilityPayment.getAggregatedClaimId().trim() + "_PHC";

        CppPaymentRequest request = new CppPaymentRequest();
        request.setClaimId(claimId);
        request.setClaimType("SUBMISSION");
        request.setClaimStatus("APPROVED");
        request.setFid(provider.getFidCode() != null ? provider.getFidCode() : String.valueOf(facilityPayment.getProviderId()));
        request.setProviderName(provider.getName());
        request.setCounty(provider.getCounty() != null ? provider.getCounty() : "UNKNOWN");
        request.setOwnershipType(provider.getFacilityAgent() != null ? provider.getFacilityAgent() : "UNKNOWN");
        request.setBeneficiaryName("SHA");
        request.setBeneficiaryShaId("SHA");
        request.setBeneficiaryCrNumber("SHA");
        request.setPrimaryContributorNumber("SHA");
        request.setApprovedClaimAmount(facilityPayment.getApprovedAmount());
        request.setFundSchemeType("PHC");
        request.setFundSchemeAmount(facilityPayment.getApprovedAmount());
        request.setClaimDate(data.serviceDateStr());
        request.setServiceDate(data.serviceDateStr());
        request.setProviderInvoiceDate(data.serviceDateStr());
        request.setInvoiceNumber(data.invoiceNumber());
        request.setClaimReferenceLink(erpClaimReferenceLink + claimId);

        // Set serviceCode - only include first one with amount matching fundSchemeAmount
        if (!data.interventionCodes().isEmpty()) {
            InterventionCodeRequest firstCode = data.interventionCodes().get(0);
            InterventionCodeRequest serviceCode = new InterventionCodeRequest();
            serviceCode.setServiceCodeType(firstCode.getServiceCodeType());
            serviceCode.setAmount(facilityPayment.getApprovedAmount());
            request.setServiceCode(List.of(serviceCode));
        }
        request.setVerifiedBy(StringUtils.isNotBlank(userId) ? userId : "SHA Admin");

        return request;
    }

    private List<CreateClaimFundScheme.FundScheme> createPhcFundSchemes(BigDecimal approvedAmount) {
        CreateClaimFundScheme.FundScheme.FundSchemeBuilder fundSchemeBuilder = CreateClaimFundScheme.FundScheme.builder();
        fundSchemeBuilder.fundSchemeType("PHC");
        fundSchemeBuilder.amount(approvedAmount);

        return List.of(fundSchemeBuilder.build());
    }

    private String extractVendorCode(Provider provider, Long providerId) {
        if (provider.getFidCode() != null) {
            return provider.getFidCode().replaceFirst("^MFL-?", "");
        }
        return String.valueOf(providerId);
    }

    private String generateInvoiceNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
    }

    // ===== COMMON ENTITY FINDER METHODS =====

    private PhcBatchPayment findBatchPaymentById(Long batchPaymentId) {
        return phcBatchPaymentRepository.findById(batchPaymentId)
                .orElseThrow(() -> new BadRequestException("Batch payment not found: " + batchPaymentId));
    }

    private PhcClaimPayment findFacilityPaymentById(Long facilityPaymentId) {
        return phcClaimPaymentRepository.findById(facilityPaymentId)
                .orElseThrow(() -> new BadRequestException("Facility payment not found: " + facilityPaymentId));
    }

    private Provider findProviderById(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new BadRequestException("Provider not found: " + providerId));
    }

    // ===== VALIDATION HELPER METHODS =====

    private PhcBatchPayment validateBatchForErpSubmission(Long batchPaymentId) {
        PhcBatchPayment batchPayment = findBatchPaymentById(batchPaymentId);

        if (batchPayment.getStatus() != PhcBatchPayment.Status.APPROVED) {
            throw new BadRequestException("Batch payment must be approved before sending to ERP");
        }

        return batchPayment;
    }


    // ===== ERP PROCESSING HELPER METHODS =====

    private List<PhcClaimPayment> getApprovedFacilityPayments(PhcBatchPayment batchPayment) {
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batchPayment);
        List<PhcClaimPayment> approvedPayments = facilityPayments.stream()
                .filter(fp -> ExtensionStateCodeEnum.APPROVED.name().equals(fp.getStatus().trim()))
                .toList();

        if (approvedPayments.isEmpty()) {
            throw new BadRequestException("No approved facility payments found for batch payment");
        }

        return approvedPayments;
    }

    private void submitFacilityPaymentsToErp(List<PhcClaimPayment> approvedPayments) {
        for (PhcClaimPayment facilityPayment : approvedPayments) {
            String result = sendFacilityPaymentToErp(facilityPayment);
            log.info("Successfully submitted facility payment {} to ERP: {}", facilityPayment.getId(), result);
        }
    }

    /**
     * Send facility payments to ERP in batches with delays
     * Used by scheduler to avoid overwhelming ERP endpoint
     * 
     * @param batchPaymentId The batch payment ID (county)
     * @param facilityBatchSize Number of facility payments to send in each batch
     * @param delayMs Delay in milliseconds between facility payment batches
     * @param cursor The processing cursor for checkpointing (optional, can be null)
     * @return Summary message with submission results
     */
    public String sendBatchPaymentToErpWithBatching(Long batchPaymentId, int facilityBatchSize, long delayMs, PhcProcessingCursor cursor) {
        log.info("Starting batched ERP submission for batch payment: {} (facility batch size: {}, delay: {}ms)",
                batchPaymentId, facilityBatchSize, delayMs);

        try {
            PhcBatchPayment batchPayment = validateBatchForErpSubmission(batchPaymentId);
            List<PhcClaimPayment> approvedPayments = getApprovedFacilityPayments(batchPayment);

            int totalFacilities = approvedPayments.size();
            int submitted = 0;
            int failed = 0;
            int skipped = 0;

            // Resume logic: Check if we're resuming from a checkpoint
            boolean isResuming = cursor != null && cursor.getLastProcessedPaymentId() != null;
            Long lastProcessedPaymentId = isResuming ? cursor.getLastProcessedPaymentId() : null;
            
            if (isResuming) {
                log.info("Resuming ERP submission for batch {} from checkpoint - last processed payment ID: {}",
                        batchPaymentId, lastProcessedPaymentId);
            }

            // Process facility payments in batches
            for (int i = 0; i < totalFacilities; i += facilityBatchSize) {
                int endIndex = Math.min(i + facilityBatchSize, totalFacilities);
                List<PhcClaimPayment> batch = approvedPayments.subList(i, endIndex);

                log.info("Submitting facility payment batch {}-{} of {} for county batch {}",
                        i + 1, endIndex, totalFacilities, batchPaymentId);

                for (PhcClaimPayment facilityPayment : batch) {
                    try {
                        // Skip if already processed (resuming scenario)
                        if (isResuming && lastProcessedPaymentId != null && facilityPayment.getId() <= lastProcessedPaymentId) {
                            skipped++;
                            if (skipped % 10 == 0) {
                                log.debug("Skipped {} already processed facility payments", skipped);
                            }
                            continue;
                        }

                        String result = sendFacilityPaymentToErp(facilityPayment);
                        log.info("Successfully submitted facility payment {} to ERP: {}",
                                facilityPayment.getId(), result);
                        submitted++;
                        
                        // Update cursor checkpoint after EVERY facility payment
                        if (cursor != null) {
                            cursor.setLastProcessedPaymentId(facilityPayment.getId());
                            cursor.setLastProcessedBatchId(batchPaymentId);
                            cursor.setProcessedRecords((cursor.getProcessedRecords() != null ? cursor.getProcessedRecords() : 0) + 1);
                            cursorRepository.save(cursor);
                        }
                        
                    } catch (Exception e) {
                        log.error("Failed to submit facility payment {} to ERP: {}",
                                facilityPayment.getId(), e.getMessage());
                        failed++;
                        
                        // Update cursor failed count
                        if (cursor != null) {
                            cursor.setFailedRecords((cursor.getFailedRecords() != null ? cursor.getFailedRecords() : 0) + 1);
                        }
                    }
                }

                // Add delay between batches (but not after the last batch)
                if (endIndex < totalFacilities) {
                    try {
                        log.debug("Waiting {}ms before next facility payment batch", delayMs);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("ERP submission interrupted", e);
                    }
                }
            }

            // Update batch status only if all or most succeeded
            if (submitted > 0) {
                updateBatchStatusAfterErpSubmission(batchPayment);
            }

            String message = String.format(
                    "Batch payment %d ERP submission completed: %d submitted, %d failed, %d skipped out of %d facilities",
                    batchPaymentId, submitted, failed, skipped, totalFacilities);
            log.info(message);

            if (failed > 0 && submitted == 0) {
                throw new BadRequestException("All facility payments failed for batch " + batchPaymentId);
            }

            return message;

        } catch (BadRequestException ex) {
            log.error("Validation error sending batch payment {} to ERP: {}", batchPaymentId, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            String errorMsg = "Failed to send batch payment to ERP: " + e.getMessage();
            log.error("Error sending batch payment {} to ERP", batchPaymentId, e);
            throw new BadRequestException(errorMsg);
        }
    }

    public void updateBatchStatusAfterErpSubmission(PhcBatchPayment batchPayment) {
        batchPayment.setStatus(PhcBatchPayment.Status.SENT_FOR_PAYMENT_PROCESSING);
        phcBatchPaymentRepository.save(batchPayment);
    }

    private void logErpRequest(Long facilityPaymentId, CreateClaimFundScheme claimRequest) {
        try {
            String requestBodyJson = new GsonBuilder().disableHtmlEscaping().create().toJson(claimRequest);
            log.info("ERP Request for Facility Payment {}: {}", facilityPaymentId, requestBodyJson);
        } catch (Exception e) {
            log.warn("Failed to log ERP request for facility payment {}: {}", facilityPaymentId, e.getMessage());
        }
    }

    private void logCppRequest(Long facilityPaymentId, CppPaymentRequest cppRequest) {
        try {
            String requestBodyJson = new GsonBuilder().disableHtmlEscaping().create().toJson(cppRequest);
            log.info("CPP Request for Facility Payment {}: {}", facilityPaymentId, requestBodyJson);
        } catch (Exception e) {
            log.warn("Failed to log CPP request for facility payment {}: {}", facilityPaymentId, e.getMessage());
        }
    }

    private String processCppErpResponse(PhcClaimPayment facilityPayment, CppPaymentRequest cppRequest, String erpResponse) {
        if (isErpResponseSuccessful(erpResponse)) {
            return handleSuccessfulCppErpResponse(facilityPayment, cppRequest, erpResponse);
        } else {
            String errorMsg = "CPP processing failed: " + erpResponse;
            log.warn("CPP Response Error for Facility Payment {}: {}", facilityPayment.getId(), erpResponse);
            throw new BadRequestException(errorMsg);
        }
    }

    private String handleSuccessfulCppErpResponse(PhcClaimPayment facilityPayment, CppPaymentRequest cppRequest, String erpResponse) {
        try {
            ERPResponseModel erpResponseModel = objectMapper.readValue(erpResponse, ERPResponseModel.class);

            // Create a CreateClaimFundScheme for saving to ERP claims tracking (for compatibility)
            CreateClaimFundScheme claimRequestForTracking = CreateClaimFundScheme.builder()
                    .claimNo(cppRequest.getClaimId())
                    .vendorCode(cppRequest.getFid())
                    .vendorName(cppRequest.getProviderName())
                    .beneficiaryName(cppRequest.getBeneficiaryName())
                    .beneficiaryShaNo(cppRequest.getBeneficiaryShaId())
                    .claimAmount(cppRequest.getClaimAmount())
                    .invoiceNumber(cppRequest.getInvoiceNumber())
                    .claimReferenceLink(cppRequest.getClaimReferenceLink())
                    .fundScheme(List.of(CreateClaimFundScheme.FundScheme.builder()
                            .fundSchemeType(cppRequest.getFundSchemeType())
                            .amount(cppRequest.getFundSchemeAmount())
                            .build()))
                    .build();

            // Save to ERP claims tracking
            sendClaimForPaymentHandler.saveToErpClaims(false, claimRequestForTracking, erpResponseModel, null);

            // Update facility payment status
            facilityPayment.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());
            phcClaimPaymentRepository.save(facilityPayment);

            String message = "Payment successfully sent to CPP for Facility Payment: " + facilityPayment.getId();
            log.info(message);
            return message;

        } catch (Exception e) {
            log.error("Error processing successful CPP response for facility payment {}", facilityPayment.getId(), e);
            throw new BadRequestException("Failed to process CPP response " + facilityPayment.getId(), e.getCause());
        }
    }

    private String processErpResponse(PhcClaimPayment facilityPayment, CreateClaimFundScheme claimRequest, String erpResponse) {
        if (isErpResponseSuccessful(erpResponse) ) {
            return handleSuccessfulErpResponse(facilityPayment, claimRequest, erpResponse);
        } else {
            String errorMsg = "ERP processing failed: " + erpResponse;
            log.warn("ERP Response Error for Facility Payment {}: {}", facilityPayment.getId(), erpResponse);
            throw new BadRequestException(errorMsg);
        }
    }

    private boolean isErpResponseSuccessful(String erpResponse) {
        if (erpResponse == null || erpResponse.trim().isEmpty()) {
            return false;
        }

        // Try to parse as JSON first for more robust checking
        try {
            ERPResponseModel responseModel = objectMapper.readValue(erpResponse, ERPResponseModel.class);
            return (responseModel != null || Objects.equals(responseModel.getStatusCode(), "409")) ; // If parsing succeeds, consider it successful
        } catch (Exception e) {
            // Fallback to simple string checks
            return (erpResponse.contains("201") || erpResponse.contains("409")) && !erpResponse.contains("Error:");
        }
    }

    private String handleSuccessfulErpResponse(PhcClaimPayment facilityPayment, CreateClaimFundScheme claimRequest, String erpResponse) {
        try {
            ERPResponseModel erpResponseModel = objectMapper.readValue(erpResponse, ERPResponseModel.class);

            // Save to ERP claims tracking
            sendClaimForPaymentHandler.saveToErpClaims(false, claimRequest, erpResponseModel, null);

            // Update facility payment status
            facilityPayment.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());
            phcClaimPaymentRepository.save(facilityPayment);

            String message = "Payment successfully sent to ERP for Facility Payment: " + facilityPayment.getId();
            log.info(message);

            String reference = erpResponseModel.getResponseBody() != null && erpResponseModel.getResponseBody().getResult() != null
                    ? erpResponseModel.getResponseBody().getResult().getId() : null;
            return message;

        } catch (Exception e) {
            log.error("Error processing successful ERP response for facility payment {}", facilityPayment.getId());
            throw new BadRequestException("Failed to process ERP response " + facilityPayment.getId(), e.getCause());
        }
    }



    /**
     * Automatically update batch payment status and amounts based on facility payment approvals
     */
    private void updateBatchFromFacilityPayments(PhcBatchPayment batchPayment) {
        if (batchPayment == null) {
            log.warn("Cannot update batch - batch payment is null");
            throw new BadRequestException("Batch payment cannot be null");
        }

        // Get all facility payments for this batch
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batchPayment);

        if (facilityPayments.isEmpty()) {
            log.warn("No facility payments found for batch {}", batchPayment.getId());
            throw new BadRequestException("No facility payments found for batch " + batchPayment.getId());
        }

        // Calculate totals and determine status
        BatchCalculationResult result = calculateBatchStatusAndTotals(facilityPayments);

        // Update batch payment
        batchPayment.setApprovedAmount(result.totalApprovedAmount());
        batchPayment.setStatus(result.batchStatus());

        // Set approval details if any facility payments are approved
        if (result.hasApprovedPayments()) {
            batchPayment.setApprovedAt(LocalDateTime.now());
            batchPayment.setApprovedBy(HealthIdContextHolder.userId());
        }

        phcBatchPaymentRepository.save(batchPayment);

        log.info("Updated batch {} - Status: {}, Approved Amount: {}, Approved: {}/{}", batchPayment.getId(), result.batchStatus(), result.totalApprovedAmount(), result.approvedCount(), result.totalCount());
    }

    /**
     * Calculate batch status and totals based on facility payment statuses
     */
    private BatchCalculationResult calculateBatchStatusAndTotals(List<PhcClaimPayment> facilityPayments) {
        int totalCount = facilityPayments.size();
        int approvedCount = 0;
        int rejectedCount = 0;
        int pendingCount = 0;
        BigDecimal totalApprovedAmount = BigDecimal.ZERO;

        for (PhcClaimPayment payment : facilityPayments) {
            String status = payment.getStatus();

            if (ExtensionStateCodeEnum.APPROVED.name().equals(status)) {
                approvedCount++;
                if (payment.getApprovedAmount() != null) {
                    totalApprovedAmount = totalApprovedAmount.add(payment.getApprovedAmount());
                }
            } else if (ExtensionStateCodeEnum.REJECTED.name().equals(status)) {
                rejectedCount++;
            } else if (ExtensionStateCodeEnum.PENDING.name().equals(status)) {
                pendingCount++;
            }
        }

        // Determine batch status based on facility payment statuses
        PhcBatchPayment.Status batchStatus;

        if (approvedCount == totalCount) {
            // All facility payments are approved
            batchStatus = PhcBatchPayment.Status.APPROVED;
        } else if (approvedCount > 0 && pendingCount == 0) {
            // Some approved, some rejected, but none pending
            batchStatus = PhcBatchPayment.Status.APPROVED;
        } else if ((approvedCount > 0 || rejectedCount > 0) && pendingCount > 0) {
            // Some approved/rejected, some pending
            batchStatus = PhcBatchPayment.Status.PARTIAL_APPROVAL;
        } else if (rejectedCount == totalCount) {
            // All facility payments are rejected
            batchStatus = PhcBatchPayment.Status.REJECTED;
        } else {
            // Still has pending payments, no approvals yet
            batchStatus = PhcBatchPayment.Status.PENDING;
        }

        return new BatchCalculationResult(batchStatus, totalApprovedAmount, approvedCount, totalCount);
    }

    /**
     * Helper record to hold batch calculation results
     */
    private record BatchCalculationResult(
            PhcBatchPayment.Status batchStatus,
            BigDecimal totalApprovedAmount,
            int approvedCount,
            int totalCount
    ) {
        public boolean hasApprovedPayments() {
            return approvedCount > 0;
        }
    }


    /**
     * Manual trigger for PHC payment calculation for a specific month
     * Creates a cursor that will be processed by PhcProcessingScheduler
     *
     * @param monthName Month in YYYYMM format (e.g., "202509") or Mon YYYY format (e.g., "Sep 2025") - MANDATORY
     */
    public String calculatePhcPayments(String monthName) {
        try {
            validateInput(monthName);

            log.info("Initializing PHC payment calculation cursor for month: {}", monthName);

            // Initialize calculation cursor - scheduler will process it automatically
            PhcCursorResponseDto response = phcBackgroundProcessingService.initializeCalculationCursor(monthName);

            return "PHC payment calculation cursor created successfully. " +
                   "Cursor ID: " + response.getCursorId() + ". " +
                   "The calculation will be processed by the background scheduler. " +
                   "You can track progress using the cursor status endpoints.";

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating PHC calculation cursor for month {}: {}", monthName, e.getMessage(), e);
            throw new BadRequestException("Failed to create calculation cursor: " + e.getMessage(), e);
        }
    }

    private void validateInput(String monthName) {
        if (monthName == null || monthName.trim().isEmpty()) {
            throw new BadRequestException("monthName is required. Expected YYYYMM or Mon YYYY format");
        }
    }

    private YearMonth parseMonth(String monthName) {
        String trimmed = monthName.trim();

        if (trimmed.length() == 6 && trimmed.matches(YYYYMM_PATTERN)) {
            return parseYYYYMM(trimmed);
        } else {
            return parseMonYYYY(trimmed);
        }
    }

    private YearMonth parseYYYYMM(String monthName) {
        int year = Integer.parseInt(monthName.substring(0, 4));
        int month = Integer.parseInt(monthName.substring(4, 6));

        if (month < 1 || month > 12) {
            throw new BadRequestException("Invalid month: " + month + ". Must be between 01-12");
        }

        return YearMonth.of(year, month);
    }

    private YearMonth parseMonYYYY(String monthName) {
        try {
            return YearMonth.parse(monthName, MON_YYYY_FORMATTER);
        } catch (Exception e) {
            throw new BadRequestException("Invalid month format. Expected YYYYMM or Mon YYYY, got: " + monthName);
        }
    }

    /**
     * Convert PhcBatchPayment entity to DTO
     */
    private PhcBatchPaymentDto convertToDto(PhcBatchPayment entity) {
        String monthYear = entity.getDate().format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH));
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(entity);
        int totalClaims = calculateTotalClaims(facilityPayments);
        String countyName = extractCountyName(entity);
        String adminLevelCode = extractAdminLevelCode(entity);

        PhcBatchPaymentDto.PhcBatchPaymentDtoBuilder dtoBuilder = PhcBatchPaymentDto.builder();
        dtoBuilder.id(entity.getId());
        dtoBuilder.countyName(countyName);
        dtoBuilder.adminLevelCode(adminLevelCode);
        dtoBuilder.date(entity.getDate());
        dtoBuilder.amount(entity.getAmount());
        dtoBuilder.approvedAmount(entity.getApprovedAmount());
        dtoBuilder.paidAmount(entity.getPaidAmount());
        dtoBuilder.status(entity.getStatus().name().trim());
        dtoBuilder.remarks(entity.getRemarks());
        dtoBuilder.createdAt(entity.getCreationDate());
        dtoBuilder.approvedAt(entity.getApprovedAt());
        dtoBuilder.approvedBy(entity.getApprovedBy());
        dtoBuilder.numberOfFacilities(facilityPayments.size());
        dtoBuilder.totalClaims(totalClaims);
        dtoBuilder.monthYear(monthYear);

        return dtoBuilder.build();
    }

    private int calculateTotalClaims(List<PhcClaimPayment> facilityPayments) {
        return facilityPayments.stream()
                .mapToInt(fp -> fp.getNumberOfClaims().intValue())
                .sum();
    }

    private String extractCountyName(PhcBatchPayment entity) {
        if (entity.getAdminLevelPopulation() != null && entity.getAdminLevelPopulation().getAdminLevelEntity() != null) {
            return entity.getAdminLevelPopulation().getAdminLevelEntity().getName();
        }
        return "Unknown County";
    }

    private String extractAdminLevelCode(PhcBatchPayment entity) {
        if (entity.getAdminLevelPopulation() != null && entity.getAdminLevelPopulation().getAdminLevelEntity() != null) {
            return entity.getAdminLevelPopulation().getAdminLevelEntity().getCode();
        }
        return null;
    }

    /**
     * Convert PhcClaimPayment entity to DTO
     */
    private PhcClaimPaymentDto convertToDto(PhcClaimPayment entity) {
        String monthYear = formatMonthYear(entity.getBatchPreparationDate());
        PhcProviderDto provider = buildProviderDto(entity.getProviderId());
        Long batchPaymentId = entity.getPhcBatchPayment() != null ? entity.getPhcBatchPayment().getId() : null;

        PhcClaimPaymentDto.PhcClaimPaymentDtoBuilder dtoBuilder = PhcClaimPaymentDto.builder();
        dtoBuilder.id(entity.getId());
        dtoBuilder.provider(provider);
        dtoBuilder.aggregatedClaimId(entity.getAggregatedClaimId());
        dtoBuilder.amount(entity.getAmount());
        dtoBuilder.approvedAmount(entity.getApprovedAmount());
        dtoBuilder.paidAmount(entity.getPaidAmount());
        dtoBuilder.numberOfClaims(BigDecimal.valueOf(entity.getNumberOfClaims()));
        dtoBuilder.batchPreparationDate(entity.getBatchPreparationDate());
        dtoBuilder.paymentDate(entity.getPaymentDate());
        dtoBuilder.status(entity.getStatus().trim());
        dtoBuilder.remarks(entity.getRemarks());
        dtoBuilder.createdAt(entity.getCreationDate());
        dtoBuilder.approvedAt(entity.getApprovedAt());
        dtoBuilder.approvedBy(entity.getApprovedBy());
        dtoBuilder.batchPaymentId(batchPaymentId);
        dtoBuilder.monthYear(monthYear);

        return dtoBuilder.build();
    }

    private String formatMonthYear(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH)) : "";
    }

    private PhcProviderDto buildProviderDto(Long providerId) {
        if (providerId == null) {
            return null;
        }

        Optional<Provider> providerOpt = providerRepository.findById(providerId);
        if (providerOpt.isPresent()) {
            Provider providerEntity = providerOpt.get();
            PhcProviderDto.PhcProviderDtoBuilder providerBuilder = PhcProviderDto.builder();
            providerBuilder.id(providerEntity.getId());
            providerBuilder.name(providerEntity.getName());
            providerBuilder.level(providerEntity.getLevel());

            return providerBuilder.build();
        }

        return null;
    }

    public String processFacilityPaymentAction(PhcFacilityPaymentActionRequest actionRequest) {
        log.info("Starting facility payment action processing for {} items by user: {}",
                actionRequest.getItems() != null ? actionRequest.getItems().size() : 0, HealthIdContextHolder.userId());

        // Validate request
        if (actionRequest.getItems() == null || actionRequest.getItems().isEmpty()) {
            throw new BadRequestException("Facility payment items are required");
        }

        int processedCount = 0;
        Set<PhcBatchPayment> batchesToUpdate = new HashSet<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (PhcFacilityPaymentActionRequest.FacilityPaymentItem item : actionRequest.getItems()) {
            try {
                // Validate action (fail fast for validation errors)
                validateFacilityPaymentActionItem(item);

                // Find and validate payment
                PhcClaimPayment facilityPayment = findAndValidateFacilityPayment(item.getId());

                // Check if already processed (warning, not error)
                if (isPaymentAlreadyProcessed(facilityPayment)) {
                    String warning = String.format("Facility payment %d already processed (status=%s)",
                            item.getId(), facilityPayment.getStatus());
                    log.warn(warning);
                    warnings.add(warning);
                    continue;
                }

                // Apply action
                applyFacilityPaymentAction(facilityPayment, item);

                // Save and track for batch update
                phcClaimPaymentRepository.save(facilityPayment);
                batchesToUpdate.add(facilityPayment.getPhcBatchPayment());
                processedCount++;

                log.debug("Successfully processed facility payment {} with action {}", item.getId(), item.getAction());

            } catch (BadRequestException ex) {
                log.error("Validation error for facility payment {}: {}", item.getId(), ex.getMessage());
                throw ex; // Fail fast for validation errors
            } catch (Exception e) {
                String errorMsg = String.format("Error processing facility payment %d: %s", item.getId(), e.getMessage());
                log.error(errorMsg, e);
                errors.add(errorMsg); // Collect processing errors and continue
            }
        }

        // Update batches after facility updates
        batchesToUpdate.forEach(this::updateBatchFromFacilityPayments);

        // Build result message
        String message = buildProcessingResultMessage(processedCount, actionRequest.getItems().size(), errors.size());
        log.info("Facility payment processing completed: {}", message);

        if (!errors.isEmpty()) {
            throw new BadRequestException(message);
        }
        return message;
    }

    private void approvePayment(PhcClaimPayment facilityPayment, PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        facilityPayment.setStatus(ExtensionStateCodeEnum.APPROVED.name());
        facilityPayment.setApprovedAmount(item.getApprovedAmount() != null ? item.getApprovedAmount() : facilityPayment.getAmount());
        setTrailInfo(facilityPayment, "Approved: " + (item.getRemarks() != null ? item.getRemarks() : ""));
    }

    private void rejectPayment(PhcClaimPayment facilityPayment, PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        facilityPayment.setStatus(ExtensionStateCodeEnum.REJECTED.name());
        setTrailInfo(facilityPayment, "Rejected: " + item.getRemarks());
    }

    private void pendingPayment(PhcClaimPayment facilityPayment, PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        facilityPayment.setStatus(ExtensionStateCodeEnum.PENDING.name());
        setTrailInfo(facilityPayment, "Pending: " + (item.getRemarks() != null ? item.getRemarks() : ""));
    }


    // ===== CENTRALIZED VALIDATION HELPER METHODS =====

    private void validateFacilityPaymentActionItem(PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        if (item.getAction() == null) {
            throw new BadRequestException("Action is required for facility payment: " + item.getId());
        }

        validateApprovalAction(item);
        validateRejectionAction(item);
    }

    private void validateApprovalAction(PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        if (item.getAction() == PhcFacilityPaymentActionRequest.FacilityPaymentAction.APPROVE) {
            if (item.getApprovedAmount() == null || item.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Approved amount must be greater than zero for facility payment");
            }
        }
    }

    private void validateRejectionAction(PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        if (item.getAction() == PhcFacilityPaymentActionRequest.FacilityPaymentAction.REJECT
                && (item.getRemarks() == null || item.getRemarks().trim().isEmpty())) {
            throw new BadRequestException("Rejection remarks are required for facility payment");
        }
    }

    private PhcClaimPayment findAndValidateFacilityPayment(Long facilityPaymentId) {
        PhcClaimPayment facilityPayment = findFacilityPaymentById(facilityPaymentId);

        if (facilityPayment.getAmount() == null) {
            throw new BadRequestException("Facility payment has no original amount: " + facilityPaymentId);
        }

        return facilityPayment;
    }

    private boolean isPaymentAlreadyProcessed(PhcClaimPayment facilityPayment) {
        return ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equals(facilityPayment.getStatus())
                || ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name().equals(facilityPayment.getStatus());
    }

    private void applyFacilityPaymentAction(PhcClaimPayment facilityPayment, PhcFacilityPaymentActionRequest.FacilityPaymentItem item) {
        // Additional validation for approved amount vs original amount
        if (item.getAction() == PhcFacilityPaymentActionRequest.FacilityPaymentAction.APPROVE
                && item.getApprovedAmount().compareTo(facilityPayment.getAmount()) > 0) {
            throw new BadRequestException("Approved amount cannot exceed original amount for facility payment: " + item.getId());
        }

        switch (item.getAction()) {
            case APPROVE:
                approvePayment(facilityPayment, item);
                break;
            case REJECT:
                rejectPayment(facilityPayment, item);
                break;
            case PENDING:
                pendingPayment(facilityPayment, item);
                break;
            default:
                throw new BadRequestException("Unsupported action: " + item.getAction());
        }
    }

    private String buildProcessingResultMessage(int processedCount, int totalCount, int errorCount) {
        if (errorCount == 0) {
            return String.format("Successfully processed %d out of %d facility payments", processedCount, totalCount);
        } else {
            return String.format("Processed %d out of %d facility payments with %d errors", processedCount, totalCount, errorCount);
        }
    }

    // ===== CENTRALIZED AUDIT HELPER METHODS =====

    private void setTrailInfo(PhcClaimPayment facilityPayment, String remarks) {
        facilityPayment.setApprovedAt(LocalDateTime.now());
        facilityPayment.setApprovedBy(HealthIdContextHolder.userId());
        facilityPayment.setRemarks(remarks);
    }

    /**
     * Mark batch payment as completed
     */
    public String markBatchPaymentCompleted(Long batchPaymentId, String completedBy, String remarks) {
        log.info("Starting batch payment completion for batch: {} by user: {}", batchPaymentId, completedBy);

        try {
            PhcBatchPayment batchPayment = validateBatchForCompletion(batchPaymentId);

            int updatedFacilities = markBatchAndFacilitiesCompleted(batchPayment, remarks);

            String message = String.format("Successfully marked batch payment %d as completed with %d facility payments",
                    batchPaymentId, updatedFacilities);
            log.info(message);

            return message;

        } catch (BadRequestException ex) {
            log.error("Validation error marking batch payment {} as completed", batchPaymentId);
            throw ex;
        } catch (Exception e) {
            String errorMsg = "Failed to mark batch payment as completed: " + e.getMessage();
            log.error("Error marking batch payment {} as completed: {}", batchPaymentId, e.getMessage(), e);
            throw new BadRequestException(errorMsg, e);
        }
    }

    private PhcBatchPayment validateBatchForCompletion(Long batchPaymentId) {
        PhcBatchPayment batchPayment = findBatchPaymentById(batchPaymentId);

        if (batchPayment.getStatus() != PhcBatchPayment.Status.SENT_FOR_PAYMENT_PROCESSING) {
            throw new BadRequestException("Batch payment must be in SENT_FOR_PAYMENT_PROCESSING status to mark as completed");
        }

        return batchPayment;
    }

    private int markBatchAndFacilitiesCompleted(PhcBatchPayment batchPayment, String remarks) {
        // Update batch payment status
        batchPayment.setStatus(PhcBatchPayment.Status.PAYMENT_COMPLETED);
        batchPayment.setPaidAmount(batchPayment.getApprovedAmount());
        batchPayment.setRemarks(remarks);

        // Mark all associated facility payments as completed
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batchPayment);
        int updatedCount = 0;

        for (PhcClaimPayment facilityPayment : facilityPayments) {
            if (ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name().equals(facilityPayment.getStatus())) {
                facilityPayment.setStatus(ExtensionStateCodeEnum.PAYMENT_COMPLETED.name());
                facilityPayment.setPaidAmount(facilityPayment.getApprovedAmount());
                facilityPayment.setRemarks(remarks);
                updatedCount++;
            }
        }

        phcBatchPaymentRepository.save(batchPayment);
        phcClaimPaymentRepository.saveAll(facilityPayments);

        return updatedCount;
    }

    // ==================== HELPER METHODS FOR REPORTS ====================

    private YearMonth parseMonthFromString(String monthName) {
        String decoded = monthName.replace("%20", " ");
        
        // Try YYYYMM format first
        if (decoded.matches(YYYYMM_PATTERN)) {
            return parseYYYYMM(decoded);
        }
        
        // Try Mon YYYY format
        return parseMonYYYY(decoded);
    }

    private Long extractPopulationCount(PhcBatchPayment batchPayment) {
        if (batchPayment.getAdminLevelPopulation() != null) {
            return batchPayment.getAdminLevelPopulation().getPopulation();
        }
        return 0L;
    }

    private String extractPatientName(InsuranceClaim claim) {
        if (claim.getPatient() != null && claim.getPatient().getName() != null) {
            return claim.getPatient().getName();
        }
        return "";
    }

    private String extractPatientGender(InsuranceClaim claim) {
        if (claim.getPatient() != null) {
            return claim.getPatient().getGender();
        }
        return "";
    }

    private Integer extractPatientAge(InsuranceClaim claim) {
        if (claim.getPatient() != null && claim.getPatient().getBirthDate() != null) {
            // Calculate age from birthDate
            Date birthDate = claim.getPatient().getBirthDate();
            Calendar birthCal = Calendar.getInstance();
            birthCal.setTime(birthDate);
            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }
            return age;
        }
        return 0;
    }

    private String extractNationalId(InsuranceClaim claim) {
        if (claim.getPatient() != null) {
            return claim.getPatient().getNationalId();
        }
        return "";
    }

    private String extractIcdCodes(InsuranceClaim claim) {
        // Extract ICD codes from claim
        if (claim.getIcdCodes() != null) {
            return claim.getIcdCodes();
        }
        return "";
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        
        // Escape quotes and wrap in quotes if contains comma, newline, or quote
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }

    // ==================== NEW SIMPLIFIED REPORT METHODS ====================

    /**
     * Get all county batch payments for a specific month
     */
    private List<PhcBatchPaymentDto> getBatchesByMonth(String month) {
        log.info("Getting batch summaries for month: {}", month);
        
        try {
            YearMonth targetMonth = parseMonthFromString(month);
            LocalDate startDate = targetMonth.atDay(1);
            LocalDate endDate = targetMonth.atEndOfMonth();
            List<PhcBatchPayment> batches = phcBatchPaymentRepository.findByDateBetween(startDate, endDate);
            
            List<PhcBatchPaymentDto> results = new ArrayList<>();
            
            for (PhcBatchPayment batch : batches) {
                // Get provider count for this batch
                List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository
                        .findByPhcBatchPayment(batch);
                
                // Get total claims count
                int totalClaims = facilityPayments.stream()
                        .mapToInt(fp -> fp.getNumberOfClaims().intValue())
                        .sum();
                
                String countyName = "Unknown";
                if (batch.getAdminLevelPopulation() != null && 
                    batch.getAdminLevelPopulation().getAdminLevelEntity() != null) {
                    countyName = batch.getAdminLevelPopulation().getAdminLevelEntity().getName();
                }
                
                PhcBatchPaymentDto dto = PhcBatchPaymentDto.builder()
                        .id(batch.getId())
                        .countyName(countyName)
                        .adminLevelCode((batch.getAdminLevelPopulation() != null && batch.getAdminLevelPopulation().getAdminLevelEntity() != null)
                                ? batch.getAdminLevelPopulation().getAdminLevelEntity().getCode()
                                : null)
                        .population(extractPopulationCount(batch))
                        .date(batch.getDate())
                        .monthYear(batch.getDate().toString().substring(0, 7)) // YYYY-MM format
                        .numberOfFacilities(facilityPayments.size())
                        .totalClaims(totalClaims)
                        .amount(batch.getAmount())
                        .approvedAmount(batch.getApprovedAmount())
                        .paidAmount(batch.getPaidAmount())
                        .status(batch.getStatus().name())
                        .remarks(batch.getRemarks())
                        .createdAt(batch.getCreationDate())
                        .approvedAt(batch.getApprovedAt())
                        .approvedBy(batch.getApprovedBy())
                        .build();
                
                results.add(dto);
            }
            
            log.info("Found {} batch summaries for month: {}", results.size(), month);
            return results;
            
        } catch (Exception e) {
            log.error("Error getting batch summaries: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to get batch summaries: " + e.getMessage(), e);
        }
    }

    /**
     * Get all provider payments for a specific batch (used internally by export)
     */
    private List<PhcClaimPaymentDto> getProvidersByBatch(Long batchId) {
        log.info("Getting provider payments for batch: {}", batchId);
        
        try {
            PhcBatchPayment batch = phcBatchPaymentRepository.findById(batchId)
                    .orElseThrow(() -> new BadRequestException("Batch not found with ID: " + batchId));
            
            List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository
                    .findByPhcBatchPayment(batch);
            
            List<PhcClaimPaymentDto> results = new ArrayList<>();
            
            for (PhcClaimPayment facilityPayment : facilityPayments) {
                Long providerIdLong = facilityPayment.getProviderId();
                Provider provider = providerRepository.findById(providerIdLong)
                        .orElse(null);
                
                if (provider == null) {
                    log.warn("Provider not found for ID: {}", providerIdLong);
                    continue;
                }
                
                PhcProviderDto providerDto = PhcProviderDto.builder()
                        .id(provider.getId())
                        .name(provider.getName())
                        .level(provider.getLevel())
                        .build();
                
                PhcClaimPaymentDto dto = PhcClaimPaymentDto.builder()
                        .id(facilityPayment.getId())
                        .provider(providerDto)
                        .aggregatedClaimId(facilityPayment.getAggregatedClaimId())
                        .numberOfClaims(facilityPayment.getNumberOfClaims() != null ? 
                                BigDecimal.valueOf(facilityPayment.getNumberOfClaims()) : BigDecimal.ZERO)
                        .amount(facilityPayment.getAmount())
                        .approvedAmount(facilityPayment.getApprovedAmount())
                        .paidAmount(facilityPayment.getPaidAmount())
                        .status(facilityPayment.getStatus())
                        .remarks(facilityPayment.getRemarks())
                        .batchPaymentId(batchId)
                        .batchPreparationDate(facilityPayment.getBatchPreparationDate())
                        .paymentDate(facilityPayment.getPaymentDate())
                        .createdAt(facilityPayment.getCreationDate())
                        .approvedAt(facilityPayment.getApprovedAt())
                        .approvedBy(facilityPayment.getApprovedBy())
                        .build();
                
                results.add(dto);
            }
            
            log.info("Found {} provider payments for batch: {}", results.size(), batchId);
            return results;
            
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting provider payments: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to get provider payments: " + e.getMessage(), e);
        }
    }

    /**
     * Get claims summary for a specific provider payment
     */
    public PhcClaimsSummaryResponseDto getClaimsByPaymentId(Long paymentId) {
        return getClaimsByPaymentInternal(paymentId, null, null, false);
    }

    /**
     * Get claims summary for a specific provider payment with optional pagination
     */
    public PhcClaimsSummaryResponseDto getClaimsByPaymentId(Long paymentId, Integer page, Integer size) {
        return getClaimsByPaymentInternal(paymentId, page, size, true);
    }

    private PhcClaimsSummaryResponseDto getClaimsByPaymentInternal(Long paymentId, Integer page, Integer size, boolean paginate) {
        log.info("Getting claims for payment ID: {}{}", paymentId,
                paginate ? String.format(" (page=%s, size=%s)", page, size) : "");

        try {
            PhcClaimPayment facilityPayment = phcClaimPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new BadRequestException("Provider payment not found with ID: " + paymentId));

            Long providerIdLong = facilityPayment.getProviderId();
            Provider provider = providerRepository.findById(providerIdLong)
                    .orElse(null);

            PhcBatchPayment batch = facilityPayment.getPhcBatchPayment();
            String county = "Unknown";
            if (batch != null && batch.getAdminLevelPopulation() != null &&
                    batch.getAdminLevelPopulation().getAdminLevelEntity() != null) {
                county = batch.getAdminLevelPopulation().getAdminLevelEntity().getName();
            }
            String month = batch != null ? batch.getDate().toString().substring(0, 7) : "Unknown";

            // Get all claims linked to this facility payment
            List<InsuranceClaim> allClaims = insuranceClaimsRepository.findByPhcClaimPaymentsId(facilityPayment.getId())
                    .orElse(Collections.emptyList());

            int total = allClaims.size();
            List<InsuranceClaim> claimsPage = allClaims;

            if (paginate) {
                int p = (page == null || page < 0) ? 0 : page;
                int s = (size == null || size <= 0) ? 50 : size;
                int from = Math.min(p * s, total);
                int to = Math.min(from + s, total);
                claimsPage = allClaims.subList(from, to);
            }

            List<PhcClaimDetailDto> claimDetails = new ArrayList<>();

            for (InsuranceClaim claim : claimsPage) {
                PhcClaimDetailDto dto = PhcClaimDetailDto.builder()
                        .claimNumber(claim.getClaimNumber())
                        .claimId(claim.getClaimId() != null ? claim.getClaimId().toString() : null)
                        .externalId(claim.getExternalId())
                        .patientName(extractPatientName(claim))
                        .patientGender(extractPatientGender(claim))
                        .patientAge(extractPatientAge(claim))
                        .nationalId(extractNationalId(claim))
                        .dateFrom(claim.getDateFrom() != null ? claim.getDateFrom().toLocalDate() : null)
                        .dateTo(claim.getDateTo() != null ? claim.getDateTo().toLocalDate() : null)
                        .totalAmount(claim.getClaimedTotal() != null ? claim.getClaimedTotal() : BigDecimal.ZERO)
                        .approvedAmount(claim.getApprovedTotal())
                        .icdCodes(extractIcdCodes(claim))
                        .status(claim.getStatus() != null ? claim.getStatus().name() : null)
                        .build();

                claimDetails.add(dto);
            }

            PhcClaimsSummaryResponseDto response = PhcClaimsSummaryResponseDto.builder()
                    .paymentId(paymentId)
                    .providerId(provider != null ? String.valueOf(provider.getId()) : providerIdLong.toString())
                    .providerName(provider != null ? provider.getName() : "Unknown")
                    .county(county)
                    .month(month)
                    .totalClaims(total)
                    .claims(claimDetails)
                    .build();

            log.info("Found {} claims (total={}) for payment ID: {}", claimDetails.size(), total, paymentId);
            return response;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting claims by payment ID: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to get claims: " + e.getMessage(), e);
        }
    }

    /**
     * Export claims summary for a specific provider payment to ZIP (CSV format)
     */
    public Map<String, Object> exportClaimsByPayment(Long paymentId) {
        log.info("Exporting claims for payment ID: {} (CSV format)", paymentId);
        long startTime = System.currentTimeMillis();

        try {
            PhcClaimsSummaryResponseDto summary = getClaimsByPaymentInternal(paymentId, null, null, false);

            if (summary.getClaims() == null || summary.getClaims().isEmpty()) {
                throw new BadRequestException("No claims found for export");
            }

            // Generate CSV content
            ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvOutputStream, false, StandardCharsets.UTF_8);

            // Get provider details for enhanced columns
            PhcClaimPayment facilityPayment = phcClaimPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new BadRequestException("Provider payment not found"));
            Provider provider = providerRepository.findById(facilityPayment.getProviderId()).orElse(null);
            String facilityLevel = provider != null ? provider.getLevel() : "";
            String facilityName = provider != null ? provider.getName() : "";
            String ownership = provider != null ? provider.getFacilityAgent() : "";
            String mflCode = provider != null ? provider.getMflCode() : "";
            String fidCode = provider != null ? provider.getFidCode() : "";

            // Write CSV header with enhanced columns
            writer.println("S/No,Claim ID,External ID,Patient Name,Gender,Age,National ID,Date From,Date To,Facility Level,Facility Name,Ownership,MFL Code,FID Code,Intervention Code,Intervention Name,Claimed Amount,ICD Codes,Status");
            Long claimNo = 1L;
            // Write data rows with enhanced columns
            for (PhcClaimDetailDto dto : summary.getClaims()) {
                // Get claim details for intervention info
                UUID claimUuid = UUID.fromString(dto.getClaimId());
                String interventionCode = "";
                String interventionName = "";
                
                // Query claim items separately
                List<ClaimLineItem> claimItems = lineItemRepository.findByClaimIds(Collections.singletonList(claimUuid))
                        .orElse(Collections.emptyList());
                if (!claimItems.isEmpty()) {
                    ClaimLineItem firstItem = claimItems.get(0);
                    interventionCode = firstItem.getCode() != null ? firstItem.getCode() : "";
                    interventionName = firstItem.getInterventionName() != null ? firstItem.getInterventionName() : "";
                }

                writer.println(String.format("%d,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        claimNo++,
                        escapeCSV(dto.getClaimId()),
                        escapeCSV(dto.getExternalId()),
                        escapeCSV(dto.getPatientName()),
                        escapeCSV(dto.getPatientGender()),
                        dto.getPatientAge() != null ? dto.getPatientAge() : 0,
                        escapeCSV(dto.getNationalId()),
                        dto.getDateFrom(),
                        dto.getDateTo(),
                        escapeCSV(facilityLevel),
                        escapeCSV(facilityName),
                        escapeCSV(ownership),
                        escapeCSV(mflCode),
                        escapeCSV(fidCode),
                        escapeCSV(interventionCode),
                        escapeCSV(interventionName),
                        dto.getTotalAmount(),
                        escapeCSV(dto.getIcdCodes()),
                        escapeCSV(dto.getStatus())
                ));
            }

            writer.flush();
            writer.close();

            // Create ZIP file using helper method
            String csvFileName = String.format("PHC_claims_summary_by_payment_%d.csv", paymentId);
            return createZipFromCsv(csvOutputStream, csvFileName, startTime, summary.getClaims().size(), "claims");

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting claims: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to export claims: " + e.getMessage(), e);
        }
    }

    /**
     * Export county batch summaries to ZIP file (CSV format)
     */
    public Map<String, Object> exportBatchesByMonth(String month) {
        log.info("Exporting batch summaries for month: {} (CSV format)", month);
        long startTime = System.currentTimeMillis();
        
        try {
            List<PhcBatchPaymentDto> batches = getBatchesByMonth(month);
            
            if (batches.isEmpty()) {
                throw new BadRequestException("No batches found for export");
            }
            
            // Generate CSV content
            ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvOutputStream, false, StandardCharsets.UTF_8);
            
            // Write CSV header
            writer.println("Batch No,County,Population,Month,Number of Facilities,Total Claims,Amount,Approved Amount,Paid Amount,Status,Created Date");
            
            // Write data rows
            for (PhcBatchPaymentDto batch : batches) {
                writer.println(String.format("%d,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s",
                        batch.getId(),
                        escapeCSV(batch.getCountyName()),
                        batch.getPopulation(),
                        batch.getMonthYear(),
                        batch.getNumberOfFacilities(),
                        batch.getTotalClaims(),
                        batch.getAmount(),
                        batch.getApprovedAmount() != null ? batch.getApprovedAmount() : "",
                        batch.getPaidAmount() != null ? batch.getPaidAmount() : "",
                        batch.getStatus(),
                        batch.getCreatedAt()
                ));
            }
            
            writer.flush();
            writer.close();
            
            // Create ZIP file using helper method
            String csvFileName = String.format("PHC_counties_summary_batches_%s.csv", month);
            return createZipFromCsv(csvOutputStream, csvFileName, startTime, batches.size(), "batches");
            
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting batches: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to export batches: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to create ZIP file from CSV content
     */
    private Map<String, Object> createZipFromCsv(ByteArrayOutputStream csvOutputStream, String csvFileName, 
                                                  long startTime, int recordCount, String recordType) throws IOException {
        String zipFileName = csvFileName.replace(".csv", ".zip");
        ByteArrayInputStream csvStream = new ByteArrayInputStream(csvOutputStream.toByteArray());
        
        try (ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipStream = new ZipOutputStream(zipOutputStream)) {
            
            zipStream.putNextEntry(new ZipEntry(csvFileName));
            try (BufferedInputStream csvBufferedStream = new BufferedInputStream(csvStream)) {
                csvBufferedStream.transferTo(zipStream);
            }
            zipStream.closeEntry();
            zipStream.finish();
            
            long endTime = System.currentTimeMillis();
            log.info("Successfully exported {} {} to file: {} (Time: {} ms)", 
                    recordCount, recordType, zipFileName, endTime - startTime);
            
            Map<String, Object> result = new HashMap<>();
            result.put("stream", new ByteArrayResource(zipOutputStream.toByteArray()));
            result.put("fileName", zipFileName);
            
            return result;
            
        } catch (IOException e) {
            long endTime = System.currentTimeMillis();
            log.error("Error generating ZIP file (Time: {} ms): {}", endTime - startTime, e.getMessage());
            throw new BadRequestException("Failed to create ZIP file: " + e.getMessage(), e);
        }
    }

    /**
     * Export provider payments to ZIP file (CSV format)
     * Same format as exportFacilityReportByMonth but without County column
     */
    public Map<String, Object> exportProvidersByBatch(Long batchId) {
        log.info("Exporting provider payments for batch: {} (CSV format)", batchId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Get batch to extract month information
            PhcBatchPayment batch = phcBatchPaymentRepository.findById(batchId)
                    .orElseThrow(() -> new BadRequestException("Batch not found with ID: " + batchId));
            
            // Get facility payments for this batch
            List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batch);
            
            if (facilityPayments.isEmpty()) {
                throw new BadRequestException("No provider payments found for export");
            }
            
            // Get month from batch
            String month = batch.getAdminLevelPopulation() != null ? 
                    batch.getAdminLevelPopulation().getMonth() : "";
            
            // Generate CSV content
            ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvOutputStream, false, StandardCharsets.UTF_8);
            
            // Write CSV header (same as exportFacilityReportByMonth but without County)
            writer.println("Facility Name,Facility Level,MFL Code,FID Code,Month,Aggregated Claim ID,Claims Count,Amount,Approved Amount,Paid Amount,Status,Batch Preparation Date,Payment Date");
            
            // Write data rows
            for (PhcClaimPayment facility : facilityPayments) {
                Provider provider = providerRepository.findById(facility.getProviderId()).orElse(null);
                if (provider == null) continue;
                
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        escapeCSV(provider.getName()),
                        escapeCSV(provider.getLevel()),
                        escapeCSV(provider.getMflCode()),
                        escapeCSV(provider.getFidCode()),
                        month != null ? escapeCSV(month) : "",
                        escapeCSV(facility.getAggregatedClaimId()),
                        facility.getNumberOfClaims() != null ? facility.getNumberOfClaims() : 0,
                        facility.getAmount(),
                        facility.getApprovedAmount() != null ? facility.getApprovedAmount() : "",
                        facility.getPaidAmount() != null ? facility.getPaidAmount() : "",
                        facility.getStatus(),
                        facility.getBatchPreparationDate(),
                        facility.getPaymentDate() != null ? facility.getPaymentDate() : ""
                ));
            }
            
            writer.flush();
            writer.close();
            
            // Create ZIP file using helper method
            String csvFileName = String.format("PHC_facilities_report_batch_%d.csv", batchId);
            return createZipFromCsv(csvOutputStream, csvFileName, startTime, facilityPayments.size(), "providers");
            
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting providers: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to export providers: " + e.getMessage(), e);
        }
    }

    /**
     * Export all facility payments for a specific month (all counties) to ZIP (CSV format)
     */
    public Map<String, Object> exportFacilityReportByMonth(String yearMonth) {
        log.info("Exporting facility report for month: {} (CSV format)", yearMonth);
        long startTime = System.currentTimeMillis();

        try {
            YearMonth targetMonth = parseMonthFromString(yearMonth);
            LocalDate startDate = targetMonth.atDay(1);
            LocalDate endDate = targetMonth.atEndOfMonth();
            List<PhcBatchPayment> batches = phcBatchPaymentRepository.findByDateBetween(startDate, endDate);

            if (batches.isEmpty()) {
                throw new BadRequestException("No batches found for the specified month");
            }

            // Collect all facility payments from all batches
            List<PhcClaimPaymentDto> allFacilities = new ArrayList<>();
            for (PhcBatchPayment batch : batches) {
                List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batch);
                String countyName = batch.getAdminLevelPopulation() != null &&
                        batch.getAdminLevelPopulation().getAdminLevelEntity() != null
                        ? batch.getAdminLevelPopulation().getAdminLevelEntity().getName()
                        : "Unknown";

                for (PhcClaimPayment facilityPayment : facilityPayments) {
                    Provider provider = providerRepository.findById(facilityPayment.getProviderId()).orElse(null);
                    if (provider == null) continue;

                    PhcProviderDto providerDto = PhcProviderDto.builder()
                            .id(provider.getId())
                            .name(provider.getName())
                            .level(provider.getLevel())
                            .build();

                    PhcClaimPaymentDto dto = PhcClaimPaymentDto.builder()
                            .id(facilityPayment.getId())
                            .provider(providerDto)
                            .aggregatedClaimId(facilityPayment.getAggregatedClaimId())
                            .numberOfClaims(facilityPayment.getNumberOfClaims() != null ?
                                    BigDecimal.valueOf(facilityPayment.getNumberOfClaims()) : BigDecimal.ZERO)
                            .amount(facilityPayment.getAmount())
                            .approvedAmount(facilityPayment.getApprovedAmount())
                            .paidAmount(facilityPayment.getPaidAmount())
                            .status(facilityPayment.getStatus())
                            .remarks(facilityPayment.getRemarks())
                            .batchPaymentId(batch.getId())
                            .batchPreparationDate(facilityPayment.getBatchPreparationDate())
                            .paymentDate(facilityPayment.getPaymentDate())
                            .createdAt(facilityPayment.getCreationDate())
                            .approvedAt(facilityPayment.getApprovedAt())
                            .approvedBy(facilityPayment.getApprovedBy())
                            .build();

                    allFacilities.add(dto);
                }
            }

            if (allFacilities.isEmpty()) {
                throw new BadRequestException("No facility payments found for the specified month");
            }

            // Generate CSV content
            ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvOutputStream, false, StandardCharsets.UTF_8);

            // Write CSV header
            writer.println("County,Facility Name,Facility Level,MFL Code,FID Code,Month,Aggregated Claim ID,Claims Count,Amount,Approved Amount,Paid Amount,Status,Batch Preparation Date,Payment Date");

            // Write data rows
            for (PhcClaimPaymentDto facility : allFacilities) {
                PhcClaimPayment fp = phcClaimPaymentRepository.findById(facility.getId()).orElse(null);
                String county = "Unknown";
                String month = "";
                if (fp != null && fp.getPhcBatchPayment() != null &&
                        fp.getPhcBatchPayment().getAdminLevelPopulation() != null &&
                        fp.getPhcBatchPayment().getAdminLevelPopulation().getAdminLevelEntity() != null) {
                    county = fp.getPhcBatchPayment().getAdminLevelPopulation().getAdminLevelEntity().getName();
                    month = fp.getPhcBatchPayment().getAdminLevelPopulation().getMonth();
                }

                Provider prov = facility.getProvider() != null ?
                        providerRepository.findById(facility.getProvider().getId()).orElse(null) : null;

                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        escapeCSV(county),
                        facility.getProvider() != null ? escapeCSV(facility.getProvider().getName()) : "",
                        facility.getProvider() != null ? escapeCSV(facility.getProvider().getLevel()) : "",
                        prov != null ? escapeCSV(prov.getMflCode()) : "",
                        prov != null ? escapeCSV(prov.getFidCode()) : "",
                        month != null ? escapeCSV(month) : "",
                        escapeCSV(facility.getAggregatedClaimId()),
                        facility.getNumberOfClaims(),
                        facility.getAmount(),
                        facility.getApprovedAmount() != null ? facility.getApprovedAmount() : "",
                        facility.getPaidAmount() != null ? facility.getPaidAmount() : "",
                        facility.getStatus(),
                        facility.getBatchPreparationDate(),
                        facility.getPaymentDate() != null ? facility.getPaymentDate() : ""
                ));
            }

            writer.flush();
            writer.close();

            // Create ZIP file using helper method
            String csvFileName = String.format("PHC_facilities_report_%s.csv", yearMonth.replace(" ", "_"));
            return createZipFromCsv(csvOutputStream, csvFileName, startTime, allFacilities.size(), "facilities");

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting facility report: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to export facility report: " + e.getMessage(), e);
        }
    }

    /**
     * Export all claims for a specific batch to ZIP (CSV format) with enhanced columns
     */
    public Map<String, Object> exportClaimsSummaryByBatch(Long batchId) {
        log.info("Exporting claims summary for batch ID: {} (CSV format)", batchId);
        long startTime = System.currentTimeMillis();

        try {
            PhcBatchPayment batch = phcBatchPaymentRepository.findById(batchId)
                    .orElseThrow(() -> new BadRequestException("Batch not found with ID: " + batchId));

            List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batch);

            if (facilityPayments.isEmpty()) {
                throw new BadRequestException("No facility payments found for batch");
            }

            // Collect all claims from all facility payments in this batch
            List<InsuranceClaim> allClaims = new ArrayList<>();
            for (PhcClaimPayment facilityPayment : facilityPayments) {
                List<InsuranceClaim> claims = insuranceClaimsRepository.findByPhcClaimPaymentsId(facilityPayment.getId())
                        .orElse(Collections.emptyList());
                allClaims.addAll(claims);
            }

            if (allClaims.isEmpty()) {
                throw new BadRequestException("No claims found for batch");
            }

            // Generate CSV content
            ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvOutputStream, false, StandardCharsets.UTF_8);

            // Write CSV header with enhanced columns
            writer.println("S/No,Claim ID,Patient Name,Gender,Age,National ID,Date From,Date To,Facility Level,Facility Name,Ownership,MFL Code,FID Code,Intervention Code,Intervention Name,Claimed Amount,ICD Codes,Status");

            // Write data rows
            long claimNo = 1;
            for (InsuranceClaim claim : allClaims) {
                Provider provider = claim.getProvider();
                String facilityLevel = provider != null ? provider.getLevel() : "";
                String facilityName = provider != null ? provider.getName() : "";
                String ownership = provider != null ? provider.getFacilityAgent() : "";
                String mflCode = provider != null ? provider.getMflCode() : "";
                String fidCode = provider != null ? provider.getFidCode() : "";

                // Get intervention info from claim item
                String interventionCode = "";
                String interventionName = "";
                List<ClaimLineItem> claimItems = lineItemRepository.findByClaimIds(Collections.singletonList(claim.getClaimId()))
                        .orElse(Collections.emptyList());
                if (!claimItems.isEmpty()) {
                    ClaimLineItem firstItem = claimItems.get(0);
                    interventionCode = firstItem.getCode() != null ? firstItem.getCode() : "";
                    interventionName = firstItem.getInterventionName() != null ? firstItem.getInterventionName() : "";
                }

                writer.println(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        claimNo++,
                        claim.getClaimId() != null ? claim.getClaimId().toString() : "",
                        escapeCSV(extractPatientName(claim)),
                        escapeCSV(extractPatientGender(claim)),
                        extractPatientAge(claim),
                        escapeCSV(extractNationalId(claim)),
                        claim.getDateFrom() != null ? claim.getDateFrom().toLocalDate() : "",
                        claim.getDateTo() != null ? claim.getDateTo().toLocalDate() : "",
                        escapeCSV(facilityLevel),
                        escapeCSV(facilityName),
                        escapeCSV(ownership),
                        escapeCSV(mflCode),
                        escapeCSV(fidCode),
                        escapeCSV(interventionCode),
                        escapeCSV(interventionName),
                        claim.getClaimedTotal() != null ? claim.getClaimedTotal() : BigDecimal.ZERO,
                        escapeCSV(extractIcdCodes(claim)),
                        claim.getStatus() != null ? claim.getStatus().name() : ""
                ));
            }

            writer.flush();
            writer.close();

            // Create ZIP file using helper method
            String csvFileName = String.format("PHC_claims_summary_by_batch_%d.csv", batchId);
            return createZipFromCsv(csvOutputStream, csvFileName, startTime, allClaims.size(), "claims");

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting claims summary: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to export claims summary: " + e.getMessage(), e);
        }
    }
}
