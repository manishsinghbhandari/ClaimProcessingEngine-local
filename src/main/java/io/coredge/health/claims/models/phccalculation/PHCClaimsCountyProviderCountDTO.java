package io.coredge.health.claims.models.phccalculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PHCClaimsCountyProviderCountDTO {
    private String Name;
    private Long populationCount;
    private Long providerCount;
}
