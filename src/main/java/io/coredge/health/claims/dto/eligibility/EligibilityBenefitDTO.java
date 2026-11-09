package io.coredge.health.claims.dto.eligibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EligibilityBenefitDTO {
    private String schemeCode;
    private String schemeName;
    private String schemeCodeCategory;
    private LocalDate memberPolicyStartDate;
    private LocalDate memberPolicyEndDate;
    private LocalDate joinDate;
    private LocalDate endDate;
    private Integer availableBalance;
    private List<SchemeBenefitDTO> schemeBenefits;
}