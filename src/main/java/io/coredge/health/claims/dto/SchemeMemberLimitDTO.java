package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemeMemberLimitDTO {
    private Long id;
    private BenefitDTO benefit;
    private Boolean isLimitShared;
    private Boolean isLimited;
    private Double limitAmount;
    private Double limitUtilized;
    private Double limitOverUtilized;
    private Double limitBalance;
}