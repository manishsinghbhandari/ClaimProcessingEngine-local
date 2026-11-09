package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
    name = "claim_work_flow",
    indexes = {
      @Index(name = "idx_claim_id", columnList = "claim_id"),
      @Index(name = "odx_workflow_id", columnList = "work_flow_id")
    })
public class ClaimWorkFlow extends StatusBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false)
  private Long claimID;

  @Column(name = "work_flow_id", nullable = false)
  private Long workFlowID;

  @Column(name = "auto_approved", nullable = false)
  private boolean autoApproved;
}
