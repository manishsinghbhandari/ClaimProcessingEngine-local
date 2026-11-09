package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "process_notes", schema = "public")
public class ProcessNotes extends AuditableBase<String> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sequence_no", nullable = false)
  private Integer sequenceNo;

  @Column(name = "claim_id", nullable = false)
  private String claimId;

  @Column(name = "notes", nullable = true)
  private String notes;

  @Column( name = "action_date",nullable = false,updatable = false,columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime actionDate;

  @Basic
  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ExtensionStateCodeEnum status;

  @PrePersist
  public void prePresist() {
    if (this.actionDate == null) {
      this.actionDate = LocalDateTime.now();
    }
  }

}
