package io.coredge.health.claims.models.workload;

import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;


import java.time.LocalDateTime;

public record ClaimWorkloadReportResponse(
        String username,
        LocalDateTime timestamp,
        ClaimType.BucketStatus actionTaken,
        ClaimType claimType,
        ExtensionStateCodeEnum claimStatus)
{}