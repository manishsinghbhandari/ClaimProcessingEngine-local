package io.coredge.health.claims.models.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkClaimResponse {
    private int successfulClaimsUpdatedCount;
    private List<String> successfulClaims;
    private int failedClaimsCount;
    private List<String> failedClaimsReasons;
    private int failedClaimNotInDbCount;
    private List<String> failedClaimNotInDbClaims;
}
