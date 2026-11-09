package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.coredge.health.claims.utils.ServiceCodeDeserializer;
import io.coredge.health.claims.utils.ServiceCodeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateClaimFundScheme implements Serializable {
    
    @JsonProperty("vendor_code")
    private String vendorCode;

    @JsonProperty("vendor_name")
    private String vendorName;

    @JsonProperty("claim_no")
    private String claimNo;

    @JsonIgnore
    private String parentClaimNo;

    @JsonIgnore
    private Boolean hasChild;

    @JsonProperty("claim_reference_link")
    private String claimReferenceLink;

    @JsonProperty("claim_amount")
    private BigDecimal claimAmount;

    @JsonProperty("fundDistribution")
    private FundDistribution fundDistribution;

    @JsonProperty("beneficiary_sha_no")
    private String beneficiaryShaNo;

    @JsonProperty("beneficiary_name")
    private String beneficiaryName;

    @JsonProperty("service_code")
    @JsonSerialize(using = ServiceCodeSerializer.class)
    @JsonDeserialize(using = ServiceCodeDeserializer.class)
    private List<String> serviceCode;

    @JsonProperty("invoice_number")
    private String invoiceNumber;

    @JsonInclude
    @JsonProperty("payment_status")
    private String paymentStatus;

    @JsonInclude
    @JsonProperty("identifier_system")
    private String identifierSystem;

    @JsonInclude
    @JsonProperty("identifier_value")
    private String identifierValue;

    @JsonProperty("adjustment_amount")
    private BigDecimal adjustmentAmount;

    @JsonProperty("adjustment_reason")
    private String adjustmentReason;

    @JsonProperty("service_end_date")
    private String serviceEndDate;

    @JsonProperty("rejection_reason")
    private String rejection_reason;

    @JsonProperty("bank_file_reference")
    private String bank_file_reference;

    @JsonProperty("payment_date")
    private String payment_date;

    private  String claimReferenceId;

    @JsonProperty("fund_scheme")
    private List<FundScheme> fundScheme;

    List<InterventionCodeRequest> interventionCodes;

    private String invoiceDate;

    private  String verifiedBy;

    private String creationDate;

    private String claimType;

    private String claimStatus;

    private String fid;

    private String providerName;

    private String county;

    private String ownershipType;

    private String beneficiaryCrNumber;

    private String primaryContributorNumber;

    private BigDecimal approvedClaimAmount;

    private String fundSchemeType;

    private BigDecimal fundSchemeAmount;

    private String claimDate;

    private String serviceDate;

    private String providerInvoiceDate;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FundScheme {

        @JsonProperty("fund_scheme_type")
        private String fundSchemeType;

        @JsonProperty("amount")
        private BigDecimal amount;
    }
}
