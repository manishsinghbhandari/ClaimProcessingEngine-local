package io.coredge.health.claims.dto;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditUpdateRequest {

  @NotBlank(message = "Claim ID is required")
  private String claimId;

  @NotBlank(message = "Audit decision is required")
  private ExtensionStateCodeEnum auditDecision;

  @Size(max = 255, message = "Reason must not exceed 255 characters")
  private String reason;

  @NotBlank(message = "Code is required")
  private String code;

  @NotBlank(message = "Description is required")
  private String description;

  private List<InterventionDetails> interventions;
}
