package io.coredge.health.claims.models;

import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErpReprocessListClaimsRequest {
    private List<String> claimIDs;
    @Builder.Default
    private Boolean force = false;
}
