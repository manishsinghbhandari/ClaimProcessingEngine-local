package io.coredge.health.claims.models.cpp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestCppPreAuth {
    private String claimId;
    private String claimType;
    private String preAuthReferenceId;
    private String fid;
    private String beneficiaryShaId;
    private String beneficiaryName;
    private String providerName;
    private BigDecimal claimAmount;
    private BigDecimal preAuthApprovedAmount;
    private String primaryContributorNumber;
    private String beneficiaryCrNumber;
    private String claimDate;
    private String status;
}
