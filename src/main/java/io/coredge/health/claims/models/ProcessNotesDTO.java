package io.coredge.health.claims.models;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessNotesDTO {

  private String claimId;
  private String notes;
  private String reason;
  private int sequenceNo;
  private LocalDateTime actionDate;
  private ExtensionStateCodeEnum status;

}
