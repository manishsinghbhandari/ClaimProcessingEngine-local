package io.coredge.health.claims.payments.transform;

import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.utils.FhirHelper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transform class to convert CreateClaimFundScheme to PaymentProcessing entity
 * for insertion into the payment_processing table.
 */
@Component
public class PaymentProcessingTransformer {

    /**
     * Transforms CreateClaimFundScheme to PaymentProcessing entity.
     *
     * @param createClaimFundScheme the source DTO from ERP/IFS claim processing
     * @param erpType               the type of ERP system (e.g., "IFS", "ERP")
     * @param paymentStatus         the current payment status
     * @return PaymentProcessing entity ready for database insertion
     */
    public PaymentProcessing transformToPaymentProcessing(CreateClaimFundScheme createClaimFundScheme,
            String erpType,
            String paymentStatus) {
        PaymentProcessing paymentProcessing = new PaymentProcessing();

        // Map claim identification fields
        paymentProcessing.setClaimId(createClaimFundScheme.getClaimNo());
        paymentProcessing.setParentId(createClaimFundScheme.getParentClaimNo());

        // Map provider information
        paymentProcessing.setProviderId(createClaimFundScheme.getVendorCode());
        paymentProcessing.setProviderName(createClaimFundScheme.getVendorName());

        // Map amount
        paymentProcessing.setAmount(createClaimFundScheme.getClaimAmount() != null
                ? createClaimFundScheme.getClaimAmount()
                : BigDecimal.ZERO);

        // Map fund distribution amounts with null safety
        if (createClaimFundScheme.getFundDistribution() != null) {
            if (createClaimFundScheme.getFundDistribution().getProviderShare() != null) {
                paymentProcessing.setProviderShareAmount(
                        createClaimFundScheme.getFundDistribution().getProviderShare().getAmount() != null
                                ? createClaimFundScheme.getFundDistribution().getProviderShare().getAmount()
                                : BigDecimal.ZERO);
            } else {
                paymentProcessing.setProviderShareAmount(BigDecimal.ZERO);
            }

            if (createClaimFundScheme.getFundDistribution().getSystemUtilization() != null) {
                paymentProcessing.setSystemUtilizationAmount(
                        createClaimFundScheme.getFundDistribution().getSystemUtilization().getAmount() != null
                                ? createClaimFundScheme.getFundDistribution().getSystemUtilization().getAmount()
                                : BigDecimal.ZERO);
            } else {
                paymentProcessing.setSystemUtilizationAmount(BigDecimal.ZERO);
            }
        } else {
            paymentProcessing.setProviderShareAmount(BigDecimal.ZERO);
            paymentProcessing.setSystemUtilizationAmount(BigDecimal.ZERO);
        }

        // Map claim creation date
        paymentProcessing
                .setClaimCreationDate(FhirHelper.convertToDateTimeFromString(createClaimFundScheme.getCreationDate()));

        // Map patient information
        paymentProcessing.setPatientId(createClaimFundScheme.getBeneficiaryShaNo());

        // Map fund scheme - extract from fundScheme list
        paymentProcessing.setFundScheme(extractFundSchemeType(createClaimFundScheme.getFundScheme()));

        // Map ERP type and payment status
        paymentProcessing.setErpType(erpType);
        paymentProcessing.setPaymentStatus(paymentStatus);

        // Map batch ID if available
        if (createClaimFundScheme.getBank_file_reference() != null) {
            paymentProcessing.setBatchId(createClaimFundScheme.getBank_file_reference());
        }

        // Map bank reference number if available
        paymentProcessing.setBankReferenceNumber(createClaimFundScheme.getBank_file_reference());

        return paymentProcessing;
    }

    /**
     * Extracts the fund scheme type from the fund scheme list.
     * Returns the type of the first non-zero fund scheme.
     *
     * @param fundSchemeList the list of fund schemes
     * @return the fund scheme type or null if not found
     */
    private String extractFundSchemeType(List<CreateClaimFundScheme.FundScheme> fundSchemeList) {
        if (fundSchemeList == null || fundSchemeList.isEmpty()) {
            return null;
        }

        return fundSchemeList.stream()
                .filter(fs -> fs.getAmount() != null && fs.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .map(CreateClaimFundScheme.FundScheme::getFundSchemeType)
                .orElse(null);
    }

}
