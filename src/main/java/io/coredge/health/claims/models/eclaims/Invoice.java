package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Invoice {

    @JsonProperty("ClaimId")
    private String claimId;

    @JsonProperty("VendorCode")
    private String vendorCode;

    @JsonProperty("VendorName")
    private String vendorName;

    @JsonProperty("ServiceDate")
    private String serviceDate;

    @JsonProperty("ProviderInvoiceDate")
    private String providerInvoiceDate;

    @JsonProperty("ClaimAmount")
    private BigDecimal claimAmount;

    @JsonProperty("BeneficiaryName")
    private String beneficiaryName;

    @JsonProperty("BeneficiaryShaNo")
    private String beneficiaryShaNo;

    @JsonProperty("FundSchemeType")
    private String fundSchemeType;

    @JsonProperty("ServiceCode")
    private List<InterventionCodeRequest> serviceCode;

    @JsonProperty("ClaimRefLink")
    private String claimRefLink;

    @JsonProperty("ProvideInvoiceNo")
    private String provideInvoiceNo;

    @JsonProperty("VerifiedBy")
    private String verifiedBy;

    @JsonProperty("CoverageType")
    private String coverageType;

    @JsonProperty("FundDistribution")
    private FundDistribution fundDistribution;

    /* ---------------- Fund Distribution ---------------- */

    @Getter
    @Setter
    @Builder
    public static class FundDistribution {

        @JsonProperty("ProviderShare")
        private Amount providerShare;

        @JsonProperty("SystemUtilization")
        private Amount systemUtilization;
    }

    @Getter
    @Setter
    @Builder
    public static class Amount {

        @JsonProperty("Amount")
        private BigDecimal amount;
    }

}