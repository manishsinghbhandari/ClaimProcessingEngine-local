package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ERPClaimResponse {
  @JsonProperty("jsonrpc")
  private String jsonrpc;

  @JsonProperty("id")
  private Object id;

  @JsonProperty("result")
  private ClaimResult result;
}
