package io.coredge.health.claims.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeBenefitLimitDTO {
    @NotNull
    private Long benefitId;
    private Long jobGroupId; // Optional, can be null
    private Boolean isLimitShared;
    private Boolean isLimited;
    private Double limitAmount;
    private JobGroupDTO jobGroup;
    private BenefitDTO benefit;
}