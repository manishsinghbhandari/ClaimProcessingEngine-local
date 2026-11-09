package io.coredge.health.claims.models.claims;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimDataResponse {
    private UUID claimId;
    private String providerName;
    private String patientName;
    private String claimCode;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String adjustment;
    private BigDecimal claimedTotal;
    private BigDecimal approvedTotal;
    private Date dateProcessed;
    private String explanation;
    private String rejectionReason;
    private String guaranteeId;
    private String externalId;
    private LocalDate creationDate;
    private String accessCode;
    private String claimType;
    private String providerLevel;
    private ExtensionStateCodeEnum status;
    private Long claimNumber;
    private String shaCode;
    private String shaPackageCode;
    private String crId;
    private String nationalId;
    private String colourCode;
    private String bundleId;
    private boolean isIpsClaim;
    private Long count;
    private Boolean auditFlag;
    private String surveillanceStatus;

}