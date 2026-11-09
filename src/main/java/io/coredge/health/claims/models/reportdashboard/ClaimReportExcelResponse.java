package io.coredge.health.claims.models.reportdashboard;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimReportExcelResponse {
    private String claimId;
    private String facilityName;
    private String facilityFrCode;
    private String patientName;
    private String patientCrId;
    private String patientNationalId;
    private String facilityLevel;
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private String status;
    private LocalDateTime claimDate;
    private String claimType;
    private String county;
    private String subCounty;
    private String source;
}

