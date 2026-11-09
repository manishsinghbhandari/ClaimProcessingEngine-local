package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaimResponseBody {
  @JsonProperty("result")
  public ClaimResult getResult() {
    return this.result;
  }

  public void setResult(ClaimResult result) {
    this.result = result;
  }

  ClaimResult result;
}
