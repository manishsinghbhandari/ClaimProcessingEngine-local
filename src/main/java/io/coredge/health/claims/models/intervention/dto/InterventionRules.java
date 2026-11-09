package io.coredge.health.claims.models.intervention.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterventionRules {

    private Map<String, Rule> rules;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        private String scope;
        private Integer maxPerYear;
        private Integer maxPerWeek;
        private Integer minDaysBetweenUses;
        private String resetPeriod;
        private Integer maxPerAdmission;
        private Integer minAge;
        private BillingPolicy billingPolicy;
        private List<String> facilityLevels;

        //these two new fields for EyeCare
        private Integer maxPerPolicyPeriod;
        private Integer policyPeriodYears;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BillingPolicy {
        private String type; // e.g., "duration"
        private List<Band> bands;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Band {
        private Integer minDays;
        private Integer maxDays;
        private Integer ratePerDay;
        private Integer flatAmount;
    }
}

