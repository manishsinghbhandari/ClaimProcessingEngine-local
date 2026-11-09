package io.coredge.health.claims.helpers;

import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class CreateClaimFundSchemeValidator {
    public List<String> validateCreateClaimFundScheme(CreateClaimFundScheme scheme) {
        List<String> missingFields = new ArrayList<>();

        if (scheme == null) {
            missingFields.add("CreateClaimFundScheme object is null");
            return missingFields;
        }
        // Validate mandatory fields
        validateField(scheme.getClaimReferenceLink(), "Claim Reference Link", missingFields);
        validateField(scheme.getVendorCode(), "Vendor Code", missingFields);
        validateField(scheme.getVendorName(), "Vendor Name", missingFields);
        validateField(scheme.getBeneficiaryName(), "Beneficiary Name", missingFields);
        validateField(scheme.getBeneficiaryShaNo(), "Beneficiary SHA Number", missingFields);
        validateField(scheme.getClaimNo(), "Claim Number", missingFields);
        validateField(scheme.getInvoiceNumber(), "Invoice Number", missingFields);
        validateField(scheme.getServiceEndDate(), "Service End Date", missingFields);

        if (scheme.getServiceCode() == null || scheme.getServiceCode().isEmpty()) {
            missingFields.add("Service Code is missing");
        }
        // Claim Amount validation
        if (scheme.getClaimAmount() == null || scheme.getClaimAmount().compareTo(BigDecimal.ZERO) <= 0) {
            missingFields.add("Claim Amount must be greater than zero");
        }

        // Fund Scheme validation
        if (scheme.getFundScheme() == null || scheme.getFundScheme().isEmpty()) {
            missingFields.add("Fund Scheme is missing");
        } else if (scheme.getFundScheme().stream().allMatch(fs -> fs.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            missingFields.add("All Fund Scheme amounts are zero, at least one must be greater than zero");
        }

        return missingFields;
    }

    private void validateField(String field, String fieldName, List<String> missingFields) {
        if (field == null || field.trim().isEmpty()) {
            missingFields.add(fieldName + " is missing");
        }
    }
}
