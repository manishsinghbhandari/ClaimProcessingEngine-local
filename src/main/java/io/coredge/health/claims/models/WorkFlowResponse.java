package io.coredge.health.claims.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkFlowResponse {

  private Long claimId;
  private Long claimResponseId;
  private Long processInstanceKey;
  private String status;
  private String state;
}
