package io.coredge.health.claims.models.reportdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimReportPdfResponse {
    private String claimId;
    private String facilityName;
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private String status;
    private LocalDateTime claimDate;
    private String claimType;
    private String county;
    private String subCounty;
    private String source;
}
