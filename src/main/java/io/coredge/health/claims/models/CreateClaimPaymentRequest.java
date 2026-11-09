package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;

import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClaimPaymentRequest {

  @JsonProperty("rejection_reason")
  String rejection_reason;

  @JsonProperty("vendor_code")
  String vendor_code;

  @JsonProperty("vendor_name")
  String vendor_name;

  @JsonProperty("claim_no")
  String claim_no;

  @JsonProperty("claim_amount")
  int claim_amount;

  @JsonProperty("beneficiary_sha_no")
  String beneficiary_sha_no;

  @JsonProperty("beneficiary_name")
  String beneficiary_name;

  @JsonProperty("service_code")
  String service_code;

  @JsonProperty("invoice_number")
  String invoice_number;

  @JsonProperty("fund_scheme")
  ArrayList<CreateClaimFundScheme> fund_scheme;
}
