package io.coredge.health.claims.models.benefits;

import lombok.Builder;

import java.util.List;

@Builder
public record BenefitsMasterDropDownResponse(List<String> providerPaymentMechanisms,
        List<String> limitsIndividualHouseholds,
        List<String> interventionQuantityYears, List<String> appliesToSchemes,
        List<String> bedTypes, List<String> accessCodes, List<String> genders,
        List<String> needsPreAuths,
        List<KeyValueResponse> documentTypes,
        List<KeyValueResponse> aiValidationDropDown,
        List<KeyValueResponse> aiRoutes) {

    public record KeyValueResponse(Long value, String key) {
    }

}
