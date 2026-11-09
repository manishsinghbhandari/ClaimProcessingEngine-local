package io.coredge.health.claims.models.reportdashboard;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClaimReportUiResponse(
        UUID claimId,
        String facilityName,
        String fidCode,
        String facilityLevel,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        ExtensionStateCodeEnum status,
        LocalDateTime claimDate,
        String claimType
) {}
