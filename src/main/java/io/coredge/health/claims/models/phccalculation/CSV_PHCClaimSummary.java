package io.coredge.health.claims.models.phccalculation;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CSV_PHCClaimSummary {
    private String claimId;
    private Long claimNo;
    private String patientId;
    private String patientName;
    private String gender;
    private int age;
    private String nationalId;
    private String mflCode;
    private String providerName;
    private String countyName;
    private String icdCodes;
    private ExtensionStateCodeEnum status;
}
