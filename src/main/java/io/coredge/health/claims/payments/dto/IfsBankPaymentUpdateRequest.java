package io.coredge.health.claims.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for IFS Bank Payment Update API.
 * Used to update payment status in IFS system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IfsBankPaymentUpdateRequest {

    /**
     * The claim identifier (e.g., "acv54370l-cdi67e-a8bu-110a8-abcdc_SHIF")
     */
    @JsonProperty("ClaimId")
    private String ClaimId;

    /**
     * Fund scheme type (e.g., "SHIF")
     */
    @JsonProperty("FundSchemeType")
    private String FundSchemeType;

    /**
     * Payment status (e.g., "Paid")
     */
    @JsonProperty("Status")
    private String Status;

    /**
     * Bank reference number
     */
    @JsonProperty("BankRefNo")
    private String BankRefNo;

    /**
     * Bank payment date in ISO 8601 format (e.g., "2025-10-14T01:01:01Z")
     */
    @JsonProperty("BankPaymentDate")
    private String BankPaymentDate;
}
