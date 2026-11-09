package io.coredge.health.claims.models.phccalculation;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "PHC_Claims_Summary")
public class PHCClaimsSummary {
    @Id
    private String id;
    private String claimId;
    private Long claimNo;
    private String patientId;
    private String patientName;
    private String gender;
    private int age;
    private String nationalId;
    private BigDecimal totalAmount;
    private Long providerId;
    private String mflCode;
    private String providerName;
    private String countyName;
    private String month;
    private BigDecimal claimWeights;
    private String icdCodes;
    private ExtensionStateCodeEnum status;
}
