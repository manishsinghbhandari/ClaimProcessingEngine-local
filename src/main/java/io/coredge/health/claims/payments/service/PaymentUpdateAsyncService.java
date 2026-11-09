package io.coredge.health.claims.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.client.ErpClient;
import io.coredge.health.claims.client.IFSClient;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.payments.dto.IfsBankPaymentUpdateRequest;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.service.ErpCallBackService;
import io.coredge.health.claims.service.ErpClaimsService;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static io.coredge.health.claims.constants.Constants.PaymentStatus.PAID;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.ErpType.IFS;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.ErpType.ODOO;

/**
 * Async service for handling ERP payment status updates.
 * Uses @Async to process ERP calls in parallel for better throughput.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUpdateAsyncService {

    private static final String PAYMENT_STATUS_PAID = "Paid";
    
    private final IFSClient ifsClient;
    private final ErpClient erpClient;
    private final ObjectMapper objectMapper;
    private final PaymentAuditService auditService;
    private final ErpCallBackService erpCallBackService;
    private final ErpClaimsService erpClaimsService;
    private final ClaimPaymentHelper claimPaymentHelper;

    /**
     * Asynchronously updates the payment status to "Paid" for a given claim.
     * Routes to the appropriate ERP (IFS, ODOO, or CPP) based on the stored ERP type.
     *
     * @param claimId     The UUID of the claim
     * @param fundType    The fund type (SHIF, PMSF, etc.)
     * @param bankRefNo   The bank reference number
     * @param paymentDate The payment date and time
     * @param erpType     The ERP type (IFS, ODOO, CPP)
     * @param batchId     The batch ID for tracking
     * @return CompletableFuture containing the payment status result
     */
    @Async
    public CompletableFuture<PaymentsEnum.PaymentStatus> updatePaymentStatusAsync(
            String claimId, 
            String fundType, 
            String bankRefNo, 
            LocalDateTime paymentDate, 
            String erpType, 
            String batchId) {
        
        log.info("Async updating payment status to Paid for claimId: {}, fundType: {}", claimId, fundType);
        PaymentsEnum.PaymentStatus currentStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
        
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
            log.debug("Payment Update Request JSON: {}", requestJson);
            String response = "";
            
            // Update payment status to ERP
            try {
                response = switch (PaymentsEnum.ErpType.valueOf(erpType)) {
                    case IFS -> updatePaymentWithIfs(ifsRequest);
                    case ODOO -> updatePaymentWithOdoo(requestJson);
                    case CPP -> updatePaymentWithCpp(requestJson);
                };
                
                log.info("ERP call successful for claimId: {}, response: {}", claimId, response);
                
                auditService.logEvent(
                        PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                        PaymentsEnum.SystemType.PAYER,
                        getSystemType(erpType),
                        currentStatus,
                        claimId, batchId,
                        requestJson, response, null, PaymentsEnum.AuditStatus.PASSED.name(),
                        "Payment status updated to ERP successfully");
                        
            } catch (Exception e) {
                currentStatus = PaymentsEnum.PaymentStatus.ERP_PAYMENT_NEEDS_REPARATION;
                log.error("ERP call failed for claimId: {}, error: {}", claimId, e.getMessage());
                auditService.logEvent(
                        PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                        PaymentsEnum.SystemType.PAYER,
                        getSystemType(erpType),
                        currentStatus,
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
            currentStatus = PaymentsEnum.PaymentStatus.ERP_PAYMENT_NEEDS_REPARATION;
        }
        
        log.info("Returning status for claimId: {}, status: {}", claimId, currentStatus);
        return CompletableFuture.completedFuture(currentStatus);
    }

    /**
     * Updates payment with IFS system.
     */
    private String updatePaymentWithIfs(IfsBankPaymentUpdateRequest request) {
        log.info("Routing payment status update to IFS");
        return ifsClient.updateBankPaymentInfo(request);
    }

    /**
     * Updates payment with Odoo ERP system.
     */
    private String updatePaymentWithOdoo(String requestJson) {
        log.info("Routing payment status update to Odoo");
        return erpClient.updateOdooErp(requestJson);
    }

    /**
     * Updates payment with CPP ERP system.
     */
    private String updatePaymentWithCpp(String requestJson) {
        log.info("Routing payment update to CPP");
        return "Cpp call back not implemented";
    }

    /**
     * Maps ERP type string to SystemType for audit logging.
     */
    private PaymentsEnum.SystemType getSystemType(String erpType) {
        try {
            PaymentsEnum.ErpType type = PaymentsEnum.ErpType.valueOf(erpType);
            if (type.equals(IFS)) {
                return PaymentsEnum.SystemType.IFS;
            } else if (type.equals(ODOO)) {
                return PaymentsEnum.SystemType.ODOO;
            }
            return PaymentsEnum.SystemType.CPP;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ERP type: {}, defaulting to CPP", erpType);
            return PaymentsEnum.SystemType.CPP;
        }
    }

    /**
     * Updates the local payment record with payment details.
     */
    private PaymentsEnum.PaymentStatus updateLocalPaymentStatus(String claimId, String bankRefNo, 
            LocalDateTime paymentDate, PaymentsEnum.ErpType erpType, String batchId) {
        log.info("Updating local DB payment status - claimId: {}, bankRefNo: {}, paymentDate: {}, erpType: {}", 
                claimId, bankRefNo, paymentDate, erpType);
        PaymentsEnum.PaymentStatus paymentStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
        
        try {
            ErpCallBackService.Validate validateResult = validateRequest(claimId, bankRefNo, paymentDate);
            log.info("Validated request for claimId: {}", claimId);
            
            erpCallBackService.claimsStatusUpdate(validateResult);
            log.info("Updated claims status for claimId: {}", claimId);
            
            paymentStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
            auditService.logEvent(
                    PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.SystemType.PAYER,
                    paymentStatus, 
                    claimId, batchId,
                    validateResult.request(), null, null, PaymentsEnum.AuditStatus.PASSED.name(),
                    "Payer table is updated successfully");
            log.info("Successfully updated payment status in payer to Paid for claimId: {}, batchId: {}", claimId, batchId);

        } catch (Exception e) {
            paymentStatus = PaymentsEnum.PaymentStatus.TABLE_NEEDS_REPARATION;
            log.error("Error updating local payment status for claimId: {}, error: {}", claimId, e.getMessage());
            auditService.logEvent(
                    PaymentsEnum.AuditEventType.PAYMENT_UPDATE,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.SystemType.PAYER,
                    paymentStatus,
                    claimId, batchId,
                    null, null, null, PaymentsEnum.AuditStatus.FAILED.name(),
                    "Payer table update failed, Message: " + e.getMessage());
            log.error("Failed to update payment status in payer to Paid for claimId: {}, batchId: {}", claimId, batchId, e);
        }
        return paymentStatus;
    }

    /**
     * Validates the payment update request.
     */
    private ErpCallBackService.Validate validateRequest(String claimId, String bankRefNo, LocalDateTime paymentDate) {
        ErpClaims byClaimNo = erpClaimsService.findByClaimNo(claimId);
        if (byClaimNo == null) {
            log.info("Invalid request. Claim number not found in DB");
            throw new BadRequestException(String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
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

        request.setPaymentStatus(PAID);
        request.setBank_file_reference(bankRefNo);
        request.setPayment_date(FhirHelper.convertToStringFromDateTime(paymentDate));

        String sanitizedClaimNo = claimId.contains("_") ? claimId.split("_")[0] : claimId;
        Claim claim = claimPaymentHelper.getClaimObject(sanitizedClaimNo);
        if (claim == null) {
            log.warn("Claim not found for ID: {}", claimId);
            throw new BadRequestException(String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
        }

        ClaimResponse claimResponse = null;
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
}
