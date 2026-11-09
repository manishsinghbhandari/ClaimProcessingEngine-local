package io.coredge.health.claims.models.phccalculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PHCClaimsCountyWiseProviderMonthDTO {
    private String countyName;
    private List<Providers> providers;

    @Builder
    @Data
    public static class Providers {
        private String providerName;
        private Long providerId;
    }
}


