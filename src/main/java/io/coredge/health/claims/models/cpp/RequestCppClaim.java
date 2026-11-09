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
public class RequestCppClaim {
    private String claimId;
    private String claimType;

    private String fid;
    private String providerName;

    private String beneficiaryShaId;
    private String beneficiaryName;

    private BigDecimal approvedClaimAmount;
    private BigDecimal claimAmount;

    private String primaryContributorNumber;
    private String beneficiaryCrNumber;
    private String claimDate;
    private String claimStatus;
    private String claimReferenceId;
    private String fundName;

    private String preAuthSequenceNumber;

    private String serviceDate;
    private String ownershipType;
    private String county;
    private String invoiceNumber;
    private String serviceCode;
}
