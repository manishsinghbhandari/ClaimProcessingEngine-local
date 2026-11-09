package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimInfoDTO {

  private Long claimId;

  private Long claimResponseId;

  private String status;

  private String state;

  private Long PatientId;

  private String gender;

  private int age;

  private boolean hasPreAuth;

  private String facilityCategory;

  private int interventionUsageCount;

  private String individualUsage;
}
