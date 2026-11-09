package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundDistribution {

    @JsonProperty("providerShare")
    private ProviderShare providerShare;

    @JsonProperty("systemUtilization")
    private SystemUtilization systemUtilization;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderShare {
        @JsonProperty("amount")
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemUtilization {
        @JsonProperty("amount")
        private BigDecimal amount;
    }

    /**
     * Factory method to create FundDistribution from provider and utilization amounts
     */
    public static FundDistribution of(BigDecimal providerAmount, BigDecimal utilizationAmount) {
        return FundDistribution.builder()
                .providerShare(ProviderShare.builder().amount(providerAmount).build())
                .systemUtilization(SystemUtilization.builder().amount(utilizationAmount).build())
                .build();
    }
}
