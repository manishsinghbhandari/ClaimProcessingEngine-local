package io.coredge.health.claims.dto.utilization;


import io.coredge.health.claims.db.entity.utilization.UtilizationPeriod;
import io.coredge.health.claims.enums.LimitScope;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PatientUtilizationResponse {

    private String crId;
    private String code;
    private String name;
    private LimitScope limitScope;
    private Integer individualMaxLimit;
    private Integer householdMaxLimit;
    private Integer individualUtilisedLimit;
    private Integer householdUtilisedLimit;
    private List<FundUtilizationResponse> fundUtilizationLimit;
    private ComputationalDetail computationalDetail;
    private IntermediatePeriodUsage intermediatePeriodUsage;
    private PerDiemUsage  perDiemUsage;

    @Data
    @Builder
    public static class FundUtilizationResponse {
        private String fundType;
        private BigDecimal utilisedAmount;
        private BigDecimal maxAmount;
        private BigDecimal availableAmount;

    }

    @Data
    @Builder
    public static class ComputationalDetail {
        private Integer individualLimitAvailableCount;
        private Integer householdLimitAvailableCount;
        private Integer intermediatePeriodAvailableCount;
        private Integer perDiemAvailableCount;
        private LocalDate coverageStartDate;
        private LocalDate coverageEndDate;
        private LocalDateTime policyStartDate;
        private LocalDateTime policyEndDate;
        private BigDecimal limitAvailableAmount;
        private LocalDate nextAvailableDate;
        private Boolean eligibility;
    }

    @Data
    @Builder
    public static class IntermediatePeriodUsage {
        private Integer individualUtilisedDuringPeriod;
        private Integer individualMaxDuringPeriod;
        private UtilizationPeriod period;
        private LocalDate lastUsageDate;
    }

    @Data
    @Builder
    public static class PerDiemUsage{
        private Integer utilisedPerDiemCount;
        private Integer maxPerDiemCount;
    }
}
