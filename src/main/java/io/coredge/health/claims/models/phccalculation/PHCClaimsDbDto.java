package io.coredge.health.claims.models.phccalculation;

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
public class PHCClaimsDbDto {
    private UUID claimId;
    private Long claimNo;
    private LocalDateTime dateFrom;
    private Long providerId;
    private String providerName;
    private String mflCode;
    private String fidCode;
    private String countyName;
    private String ownership;
    private String patientId;
    private String patientName;
    private String gender;
    private Date dateOfBirth;
    private String nationalId;
    private String icdCodes;
    private Double totalAmount;
}
