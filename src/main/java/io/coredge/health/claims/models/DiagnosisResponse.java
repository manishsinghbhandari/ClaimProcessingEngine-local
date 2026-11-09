package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiagnosisResponse {
  private Long id;
  private String claimId;
  private String patientId;
  private String code;

  @JsonProperty("diagnosisName")
  private String name;

  @JsonProperty("createdBy")
  private String createdBy;

  @JsonProperty("creationDate")
  private LocalDateTime creationDate;
}
