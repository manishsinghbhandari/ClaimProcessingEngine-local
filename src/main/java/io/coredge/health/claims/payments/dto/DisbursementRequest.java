package io.coredge.health.claims.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.coredge.health.claims.models.eclaims.FundDistribution;
import io.coredge.health.claims.models.eclaims.Invoice;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DisbursementRequest {
    private Facility facility;
    private Invoice invoice;
    private List<ClaimRequest> claims;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("result_url")
    private String resultUrl;

    private Account debit;
    private Account credit;

    @Data
    @Builder
    public static class Facility {
        private String identifier;
        private String name;
    }

    @Data
    @Builder
    public static class Invoice {
        private String number;
        @JsonProperty("batch_id")
        private String batchId;
        @JsonProperty("purchase_order_number")
        private String purchaseOrderNumber;
    }

    @Data
    @Builder
    public static class ClaimRequest {
        @JsonProperty("claim_id")
        private String claimId;
        @JsonProperty("primary_contributor")
        private String primaryContributor;
        private String beneficiary;
        private BigDecimal amount;
        private String currency;
        @JsonProperty("date_raised")
        private String dateRaised;
        @JsonProperty("fundDistribution")
        private io.coredge.health.claims.models.eclaims.Invoice.FundDistribution fundDistribution;
    }

    @Data
    @Builder
    public static class Account {
        @JsonProperty("bank_name")
        private String bankName;
        @JsonProperty("account_number")
        private String accountNumber;
        @JsonProperty("sort_code")
        private String sortCode;
        @JsonProperty("account_name")
        private String accountName;
        @JsonProperty("bank_code")
        private String bankCode;
        private String branch;
    }

    @JsonProperty("fundDistribution")
    private io.coredge.health.claims.models.eclaims.Invoice.FundDistribution fundDistribution;
}
