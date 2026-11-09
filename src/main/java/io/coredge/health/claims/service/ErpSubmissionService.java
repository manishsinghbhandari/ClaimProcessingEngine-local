package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.enums.ErpErrorCode;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.eclaims.ERPResponseModel;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.utils.ClaimsValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErpSubmissionService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ErpClaimsService erpClaimsService;
    private final InsuranceClaimsService insuranceClaimsService;
    private final ClaimsValidator claimsValidator;

    /**
     * Transactional method to update database after successful ERP submission.
     * Updates erp_claims table and claim status atomically.
     */
    @Transactional
    public void updateDatabaseAfterErpSuccess(CreateClaimFundScheme createClaimFundScheme,
                                             ERPResponseModel claimErpResponse,
                                             String claimResponseID,
                                             String bankReferenceNumber,
                                             String claimID,
                                              boolean isLastProcess) {
        try {
            log.info("Transactionally updating database after ERP success for ClaimID: {}", createClaimFundScheme.getClaimNo());

            // Save to erp_claims table
            saveToErpClaims(createClaimFundScheme, claimErpResponse, claimResponseID, bankReferenceNumber);

            // Update claim status to SENT_FOR_PAYMENT_PROCESSING
            if (isLastProcess){
            InsuranceClaim claim = insuranceClaimsService.getByExternalID(claimID);
            claimsValidator.validateClaimStatusBeforeUpdate(claim.getStatus() , ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING , claim.getUse() , claimID);
            claim.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
            claim.setLastModifiedDate(LocalDateTime.now());
            insuranceClaimsService.save(claim);
            }

            log.info("Database update completed successfully for ClaimID: {}", createClaimFundScheme.getClaimNo());
        } catch (Exception e) {
            log.error("Database update failed after ERP success for ClaimID: {}, Error: {}", createClaimFundScheme.getClaimNo(), e.getMessage(), e);
            throw new RuntimeException("Database update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sets claim status to ERROR when ERP submission or DB update fails.
     */
    @Transactional
    public void setClaimStatusToError(String claimID, ErpErrorCode errorCode, String errorMessage) {
        try {
            log.warn("Setting claim status to ERROR for ClaimID: {}, ErrorCode: {}, Message: {}", claimID, errorCode.getCode(), errorMessage);

            InsuranceClaim claim = insuranceClaimsService.getByExternalID(claimID);
            // Optionally save error details to erp_claims if record exists
            ErpClaims erpClaims = erpClaimsService.findByClaimNo(claimID);
            if (erpClaims != null) {
                erpClaims.setHttpCallMsg(errorMessage);
                erpClaims.setPaymentStatus(ExtensionStateCodeEnum.fromCode(claim.getStatus().name()).name());
                erpClaimsService.saveErpClaims(erpClaims);
            }

            log.info("Claim status set to ERROR for ClaimID: {}", claimID);
        } catch (Exception e) {
            log.error("Failed to set claim status to ERROR for ClaimID: {}, Error: {}", claimID, e.getMessage(), e);
            // Don't throw here to avoid masking original error
        }
    }

    private void saveToErpClaims(CreateClaimFundScheme createClaimFundScheme,
                                ERPResponseModel claimErpResponse,
                                String claimResponseID,
                                String bankReferenceNumber) {
        ErpClaims erpClaims = erpClaimsService.findByClaimNo(createClaimFundScheme.getClaimNo());
        if (erpClaims == null) {
            erpClaims = new ErpClaims();
        }

        BeanUtils.copyProperties(createClaimFundScheme, erpClaims, "fundDistribution");
        if (createClaimFundScheme.getParentClaimNo() != null) {
            erpClaims.setParentClaimNo(createClaimFundScheme.getParentClaimNo());
        }
        // Extract amounts from fundDistribution for database storage
        if (createClaimFundScheme.getFundDistribution() != null) {
            if (createClaimFundScheme.getFundDistribution().getProviderShare() != null) {
                erpClaims.setProviderAmount(createClaimFundScheme.getFundDistribution().getProviderShare().getAmount());
            }
            if (createClaimFundScheme.getFundDistribution().getSystemUtilization() != null) {
                erpClaims.setUtilizationAmount(createClaimFundScheme.getFundDistribution().getSystemUtilization().getAmount());
            }
        }
        erpClaims.setHttpCallStatus(claimErpResponse.getStatusCode());
        erpClaims.setHttpCallMsg(claimErpResponse.getResponseBody().getResult().getMessage());
        erpClaims.setErpId(claimErpResponse.getResponseBody().getResult().getId());
        erpClaims.setClaimResponseId(claimResponseID);
        erpClaims.setServiceCode(String.valueOf(createClaimFundScheme.getServiceCode()));
        erpClaims.setPaymentStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());

        if (bankReferenceNumber != null) {
            erpClaims.setBankReference(bankReferenceNumber);
        }

        // Save the Request JSON
        try {
            String jsonStr = objectMapper.writeValueAsString(createClaimFundScheme);
            erpClaims.setRequest(jsonStr);
        } catch (JsonProcessingException e) {
            log.warn("JsonProcessingException while saving request: {}", e.getMessage());
        }

        erpClaimsService.saveErpClaims(erpClaims);
    }
}