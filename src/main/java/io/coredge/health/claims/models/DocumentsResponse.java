package io.coredge.health.claims.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentsResponse {
  private Long id;
  private String claimId;
  private String title;
  private String docType;
  private String urlSha;
  private String urlSp;
  private String createdBy;
  private LocalDateTime creationDate;
}
