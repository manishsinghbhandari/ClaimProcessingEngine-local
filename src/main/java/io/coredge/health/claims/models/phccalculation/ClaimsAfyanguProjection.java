package io.coredge.health.claims.models.phccalculation;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimsAfyanguProjection {
    private UUID claimId;
    private Long claimNo;
    private LocalDateTime creationDate;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String mflCode;
    private String fidCode;
    private String patientId;
    private String nationalId;
    private String icdCodes;
    private BigDecimal totalAmount;
    private String use;
    private String claimType;
    private ExtensionStateCodeEnum status;
    private String accessCode;
}
