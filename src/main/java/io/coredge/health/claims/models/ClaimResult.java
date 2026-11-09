package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClaimResult {
  @JsonProperty("status_code")
  private String status_code;

  @JsonProperty("response_status")
  private String response_status;

  @JsonProperty("from_sha")
  private boolean from_sha;

  @JsonProperty("response_body")
  private ClaimResponseBody response_body;

  @JsonProperty("status")
  private String status;

  @JsonProperty("message")
  private String message;

  @JsonProperty("id")
  private String id;
}
