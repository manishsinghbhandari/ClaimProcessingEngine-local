package io.coredge.health.claims.models.eclaims;

import java.util.UUID;

public interface ClaimPreAuthMappingProjection {
    UUID getClaimId();
    UUID getPreAuthId();
}