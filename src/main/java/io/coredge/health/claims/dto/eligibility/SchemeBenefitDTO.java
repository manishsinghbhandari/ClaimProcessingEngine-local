package io.coredge.health.claims.dto.eligibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemeBenefitDTO {
    private String benefitCode;
    private String benefitName;
    private Integer limitAmount;
    private Integer usedAmount;
    private Integer availableAmount;
}