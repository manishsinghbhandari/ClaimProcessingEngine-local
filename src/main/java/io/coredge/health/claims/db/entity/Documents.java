package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Slf4j
@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@Table(name = "documents", schema = "public")
public class Documents {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Basic
  @Column(name = "claim_id", unique = false, nullable = false)
  private String claimId;

  @Basic
  @Column(name = "title", unique = false, nullable = true)
  private String title;

  @Basic
  @Column(name = "doc-type", unique = false, nullable = true)
  private String docType;

  @Basic
  @Column(name = "url_sha", unique = false, nullable = false)
  private String urlSha;

  @Basic
  @Column(name = "url_sp", unique = false, nullable = false)
  private String urlSp;

  @Column(name = "created_by")
  private String createdBy;

  @CreatedDate
  @Column(
      name = "created_date",
      nullable = false,
      updatable = false,
      columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
  protected LocalDateTime creationDate;

  @PrePersist
  public void setCreationDate() {
    if (this.creationDate == null) {
      this.creationDate = LocalDateTime.now();
    }
  }
}
