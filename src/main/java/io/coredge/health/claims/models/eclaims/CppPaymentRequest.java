package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CppPaymentRequest {
    private String claimId;
    private String claimType;
    private String fid;
    private String beneficiaryShaId;
    private String beneficiaryName;
    private String providerName;
    private BigDecimal claimAmount;
    private String primaryContributorNumber;
    private String beneficiaryCrNumber;
    private String claimDate;
    private String claimStatus;
    private String claimReferenceId;
    private String serviceDate;
    private String ownershipType;
    private String county;
    private String invoiceNumber;
    private String claimReferenceLink;
    private List<InterventionCodeRequest> serviceCode;
    private String fundSchemeType;
    private BigDecimal fundSchemeAmount;
    private String verifiedBy;
    private String providerInvoiceDate;
    private BigDecimal approvedClaimAmount;

}
