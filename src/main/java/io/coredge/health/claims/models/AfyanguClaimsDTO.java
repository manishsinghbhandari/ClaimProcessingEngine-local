package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AfyanguClaimsDTO {

    @JsonProperty("claim_id")
    private UUID claimId;

    @JsonProperty("claim_type")
    private String claimType;

    @JsonProperty("use")
    private String use;

    @JsonProperty("sub_type")
    private String subType;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("claim_net")
    private BigDecimal claimNet;

    @JsonProperty("diagnosis")
    private List<String> diagnosis;

    @JsonProperty("intervention")
    private List<String> intervention;

    @JsonProperty("patient_search_type")
    private String patientSearchType;

    @JsonProperty("patient_number")
    private String patientNumber;

    @JsonProperty("organization_id")
    private String organizationId;

    @JsonProperty("stage")
    private String stage;

    @JsonProperty("created_date")
    private String createdDate;

    @JsonProperty("admission_start_date")
    private String admissionStartDate;

    @JsonProperty("admission_end_date")
    private String admissionEndDate;

    @JsonProperty("erp")
    private ERP erp;

    @Data
    public static class ERP {
        @JsonProperty("payable_amount")
        private BigDecimal payableAmount;

        @JsonProperty("claim_amount")
        private BigDecimal claimAmount;

        @JsonProperty("payment_date")
        private String paymentDate;

        @JsonProperty("rejection_reason")
        private String rejectionReason;
    }
}
