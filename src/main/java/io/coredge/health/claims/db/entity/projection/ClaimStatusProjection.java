package io.coredge.health.claims.db.entity.projection;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;

import java.util.UUID;

public interface ClaimStatusProjection {
    UUID getClaimId();
    ExtensionStateCodeEnum getStatus();
}
