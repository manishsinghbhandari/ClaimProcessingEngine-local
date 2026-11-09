package io.coredge.health.claims.payments.service;

import io.coredge.health.claims.enums.FundType;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.client.ErpClient;
import io.coredge.health.claims.client.IFSClient;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.payments.dto.IfsBankPaymentUpdateRequest;
import io.coredge.health.claims.payments.dto.PaymentGatewayCallbackRequest;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.repository.PaymentProcessingRepository;
import io.coredge.health.claims.service.ErpCallBackService;
import io.coredge.health.claims.service.ErpClaimsService;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.coredge.health.claims.constants.Constants.PaymentStatus.PAID;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.ErpType.IFS;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.ErpType.ODOO;

/**
 * Service responsible for handling payment paid notifications
 * and updating payment status in ERP systems (IFS, ODOO, CPP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUpdateErpNotificationService {

    private final PaymentProcessingRepository processingRepository;
    private final ApplicationProperties.PaymentProcessing paymentProcessingProperties;
    private final PaymentUpdateAsyncService paymentUpdateAsyncService;

    private static final String PAYMENT_STATUS_PAID = "Paid";
    private final IFSClient ifsClient;
    private final ErpClient erpClient;
    private final ObjectMapper objectMapper;
    private final PaymentAuditService auditService;
    private final ErpCallBackService erpCallBackService;
    private final ErpClaimsService erpClaimsService;
    private final ClaimPaymentHelper claimPaymentHelper;

    /**
     * Updates payment completion status for claims in the local system and
     * (optionally) propagates the update to the corresponding ERP system.
     *
     * <p>
     * This method performs the following steps:
     * <ol>
     * <li>Fetches all {@link PaymentProcessing} records associated with the given
     * batch ID using pagination</li>
     * <li>Marks each claim as {@code PAYMENT_COMPLETED}</li>
     * <li>Updates bank reference number and payment completion timestamp</li>
     * <li>Persists the updated records in the local database in batches</li>
     * </ol>
     *
     * <p>
     * <b>Optimization:</b> Uses pagination to process large datasets (10k+ records)
     * efficiently
     * without loading all records into memory at once. ERP calls are processed
     * asynchronously
     * for improved throughput.
     *
     * <p>
     * This method assumes the batch ID uniquely identifies a group of claims
     * processed in a single payment cycle.
     *
     * @param request the payment gateway callback request containing batch ID,
     *                external bank reference, and payment completion timestamp
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusInLocalAndErp(PaymentGatewayCallbackRequest request) {
        log.info("Update status in local and erp notification for batch: {}", request.getBatchId());

        int batchSize = paymentProcessingProperties.getBatchSize();
        int pageNumber = 0;
        Page<PaymentProcessing> claimsPage;
        int totalProcessed = 0;

        do {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            claimsPage = processingRepository.findByBatchIdAndPaymentStatus(
                    request.getBatchId(),
                    PaymentsEnum.BatchStatus.PAYMENT_COMPLETED.name(),
                    pageable);

            List<PaymentProcessing> claims = claimsPage.getContent();
            if (claims.isEmpty()) {
                break;
            }

            // Process claims asynchronously using CompletableFuture
            List<CompletableFuture<PaymentsEnum.PaymentStatus>> futures = new ArrayList<>();

            for (PaymentProcessing claim : claims) {
                CompletableFuture<PaymentsEnum.PaymentStatus> future = paymentUpdateAsyncService
                        .updatePaymentStatusAsync(
                                claim.getClaimId(),
                                claim.getFundScheme(),
                                request.getExternalReference(),
                                request.getTimestamp(),
                                claim.getErpType(),
                                request.getBatchId())
                        .thenApply(status -> {
                            claim.setPaymentStatus(status.name());
                            return status;
                        });
                futures.add(future);
            }

            // Wait for all async operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect successfully processed claims
            List<PaymentProcessing> updatedClaims = new ArrayList<>();
            for (int i = 0; i < claims.size(); i++) {
                try {
                    PaymentsEnum.PaymentStatus status = futures.get(i).get();
                    if (status != null) {
                        updatedClaims.add(claims.get(i));
                    }
                } catch (Exception e) {
                    log.error("Failed to get async result for claimId: {}, error: {}",
                            claims.get(i).getClaimId(), e.getMessage());
                }
            }

            if (!updatedClaims.isEmpty()) {
                processingRepository.saveAll(updatedClaims);
                totalProcessed += updatedClaims.size();
                log.info("Processed batch {} with {} claims, total processed: {}",
                        pageNumber, updatedClaims.size(), totalProcessed);
            }

            pageNumber++;
        } while (claimsPage.hasNext());

        log.info("Completed processing for batch: {}, total claims processed: {}",
                request.getBatchId(), totalProcessed);
    }

    public PaymentsEnum.PaymentStatus updateStatusInErp(PaymentProcessing request) {
        log.info("ERP reprocessing Update status in erp notification for batch: {}", request.getBatchId());
        return updatePaymentStatus(request.getClaimId(), request.getFundScheme(), request.getBankReferenceNumber(),
                request.getPaymentCompletionTime(), request.getErpType(), request.getBatchId());
    }

    /**
     * Updates the payment status to "Paid" for a given claim and fund type.
     * Routes to the appropriate ERP (IFS, ODOO, or CPP) based on the stored ERP
     * type.
     *
     * @param claimId     The UUID of the claim
     * @param fundType    The fund type (SHIF, PMSF, etc.)
     * @param bankRefNo   The bank reference number
     * @param paymentDate The payment date and time
     */
    private PaymentsEnum.PaymentStatus updatePaymentStatus(String claimId, String fundType, String bankRefNo,
            LocalDateTime paymentDate, String erpType, String batchId) {
        log.info("Updating payment status to Paid for claimId: {}, fundType: {}", claimId, fundType);
        PaymentsEnum.PaymentStatus responseStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
        try {
            // Build the request body
            IfsBankPaymentUpdateRequest ifsRequest = IfsBankPaymentUpdateRequest.builder()
                    .ClaimId(claimId)
                    .FundSchemeType(fundType)
                    .Status(PAYMENT_STATUS_PAID)
                    .BankRefNo(bankRefNo)
                    .BankPaymentDate(paymentDate.toInstant(ZoneOffset.UTC).toString())
                    .build();

            String requestJson = objectMapper.writeValueAsString(ifsRequest);
            log.info("Payment Update Request JSON: {}", requestJson);
            String response = "";
            // Update payment status to ERP
            try {
                response = switch (PaymentsEnum.ErpType.valueOf(erpType)) {
                    case IFS -> updatePaymentWithIfs(ifsRequest);
                    case ODOO -> updatePaymentWithOdoo(requestJson);
                    case CPP -> updatePaymentWithCpp(requestJson);
                };
                auditService.logEvent(PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                        PaymentsEnum.SystemType.PAYER,
                        PaymentsEnum.ErpType.valueOf(erpType).equals(IFS) ? PaymentsEnum.SystemType.IFS
                                : PaymentsEnum.ErpType.valueOf(erpType).equals(ODOO) ? PaymentsEnum.SystemType.ODOO
                                        : PaymentsEnum.SystemType.CPP,
                        responseStatus,
                        claimId, batchId,
                        requestJson, response, null, PaymentsEnum.AuditStatus.PASSED.name(),
                        "Payment status updated to ERP successfully");
            } catch (Exception e) {
                responseStatus = PaymentsEnum.PaymentStatus.ERP_PAYMENT_NEEDS_REPARATION;
                auditService.logEvent(PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                        PaymentsEnum.SystemType.PAYER,
                        PaymentsEnum.ErpType.valueOf(erpType).equals(IFS) ? PaymentsEnum.SystemType.IFS
                                : PaymentsEnum.ErpType.valueOf(erpType).equals(ODOO) ? PaymentsEnum.SystemType.ODOO
                                        : PaymentsEnum.SystemType.CPP,
                        responseStatus,
                        claimId, batchId,
                        requestJson, response, null, PaymentsEnum.AuditStatus.FAILED.name(),
                        "ERP Payment update failed, Error: " + e.getMessage());
            }
            log.info("ERP Payment Update Response for {}: {}", erpType, response);

            // Update payers table for payment status
            updateLocalPaymentStatus(claimId, bankRefNo, paymentDate, PaymentsEnum.ErpType.valueOf(erpType), batchId);
            log.info("Successfully updated payment status to Paid for claimId: {}", claimId);

        } catch (Exception e) {
            log.error("Failed to update payment status to Paid for claimId: {}", claimId, e);
        }
        return responseStatus;
    }

    /**
     * Updates payment with IFS system.
     *
     * @param request The IFS bank payment update request
     * @return IFS API response
     */
    private String updatePaymentWithIfs(IfsBankPaymentUpdateRequest request) {
        log.info("Routing payment status update to IFS");
        return ifsClient.updateBankPaymentInfo(request);
    }

    /**
     * Updates payment with Odoo ERP system.
     * Uses the same request body as IFS.
     *
     * @param requestJson The JSON request body
     * @return Odoo ERP response
     */
    private String updatePaymentWithOdoo(String requestJson) {
        log.info("Routing payment status update to Odoo");
        return erpClient.updateOdooErp(requestJson);
    }

    /**
     * Updates payment with CPP ERP system.
     * Uses the same request body as IFS.
     *
     * @param requestJson The JSON request body
     * @return CPP ERP response
     */
    private String updatePaymentWithCpp(String requestJson) {
        log.info("Routing payment update to CPP");
        // TODO: Implement CPP client update method if different from Odoo
        // For now, using Odoo as fallback
        return "Cpp call back not implemented";
    }

    /**
     * Updates the local payment record with payment details.
     *
     * @param bankRefNo   The bank reference number
     * @param paymentDate The payment date and time
     * @param erpType     The ERP type used
     */
    private PaymentsEnum.PaymentStatus updateLocalPaymentStatus(String claimId, String bankRefNo,
            LocalDateTime paymentDate, PaymentsEnum.ErpType erpType, String batchId) {
        log.info("Updating local DB payment status - claimId: {}, - bankRefNo: {}, paymentDate: {}, erpType: {}",
                claimId, bankRefNo, paymentDate, erpType);
        PaymentsEnum.PaymentStatus paymentStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
        try {
            // Implement business logic for local status update
            ErpCallBackService.Validate validateResult = validateRequest(claimId, bankRefNo, paymentDate);
            // Update in the erp table.
            erpCallBackService.claimsStatusUpdate(validateResult);
            auditService.logEvent(PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED, claimId, batchId,
                    validateResult.request(), null, null, PaymentsEnum.AuditStatus.PASSED.name(),
                    "Payer table is updated successfully");
            log.info("Successfully to update payment status in payer to Paid for claimId: {}, batchId: {}", claimId,
                    batchId);

        } catch (Exception e) {
            paymentStatus = PaymentsEnum.PaymentStatus.TABLE_NEEDS_REPARATION;
            auditService.logEvent(PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.SystemType.PAYER,
                    paymentStatus,
                    claimId, batchId,
                    null, null, null, PaymentsEnum.AuditStatus.FAILED.name(),
                    "Payer table is update failed, Message: " + e.getMessage());
            log.error("Failed to update payment status in payer to Paid for claimId: {}, batchId: {}", claimId, batchId,
                    e);
            throw new RuntimeException(e);
        }
        return paymentStatus;
    }

    public @NotNull ErpCallBackService.Validate validateRequest(String claimId, String bankRefNo,
            LocalDateTime paymentDate) {
        ErpClaims byClaimNo = erpClaimsService.findByClaimNo(claimId);
        if (byClaimNo == null) {
            log.info("Invalid request. Claim number not found in DB");
            throw new BadRequestException(
                    String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
        }

        CreateClaimFundScheme request;
        try {
            request = objectMapper.readValue(byClaimNo.getRequest(), CreateClaimFundScheme.class);
            log.info("Update the claim status ClaimNumber: {}, paymentStatus: {} in local payer system",
                    request.getClaimNo(), request.getPaymentStatus());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ERP response: {}", e.getMessage(), e);
            throw new DataValidationException("Unable to process ERP callback", e);
        }

        if (request.getClaimNo() == null) {
            log.info("Invalid ERP response: Missing mandatory fields.");
            throw new BadRequestException("Invalid ERP callback payload.");
        }

        // update the details in the request
        request.setPaymentStatus(PAID);
        request.setBank_file_reference(bankRefNo);
        request.setPayment_date(FhirHelper.convertToStringFromDateTime(paymentDate));

        String sanitizedClaimNo = claimId.contains("_") ? claimId.split("_")[0] : claimId;
        Claim claim = claimPaymentHelper.getClaimObject(sanitizedClaimNo);
        if (claim == null) {
            log.warn("Claim not found for ID: {}", claimId);
            throw new BadRequestException(
                    String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
        }

        ClaimResponse claimResponse = null;
        // Temp Fix for the multiple claim response issue
        if (StringUtils.isNotEmpty(byClaimNo.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(byClaimNo.getClaimResponseId());
        }
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(sanitizedClaimNo);
            if (claimResponse == null) {
                log.warn("Claim response not found against claim ID: {}", claimId);
                throw new BadRequestException("Invalid Claim ID, Claim response not found");
            }
        }
        return new ErpCallBackService.Validate(request, sanitizedClaimNo, byClaimNo, claim, claimResponse);
    }

    /**
     * Formats the claim ID with fund scheme suffix.
     *
     * @param claimNumber The claim number
     * @param fundType    The fund type
     * @return Formatted claim ID (e.g., "12345_SHIF")
     */
    private String formatClaimId(Long claimNumber, FundType fundType) {
        String claimNumStr = String.valueOf(claimNumber);
        String fundSuffix = "_" + fundType.name();
        if (claimNumStr.endsWith(fundSuffix)) {
            return claimNumStr;
        }
        return claimNumStr + fundSuffix;
    }

}
