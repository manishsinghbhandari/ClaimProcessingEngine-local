package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for claims summary response by provider payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcClaimsSummaryResponseDto {
    
    private Long paymentId;
    private String providerId;
    private String providerName;
    private String county;
    private String month;
    private Integer totalClaims;
    private List<PhcClaimDetailDto> claims;
}
