package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.context.HealthIdContextHolder;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "work_flow", schema = "public")
public class WorkFlow extends StatusBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false)
  private String claimId;

  @Column(name = "claim_response_id", nullable = false)
  private String claimResponseId;

  @Column(name = "process_instance_key", nullable = false)
  private Long processInstanceKey;

  @Column(name = "gateway_ip")
  private String gatewayIp;

  @Column(name = "job_key", nullable = true)
  private Long jobKey;

  @Column(name = "active" , nullable = false , columnDefinition = "boolean default true" )
  private Boolean active;

  @PrePersist
  public void prePersist() {
    if (StringUtils.isNotBlank(HealthIdContextHolder.userId())) {
      this.createdBy = HealthIdContextHolder.userId();
      this.lastModifiedBy = HealthIdContextHolder.userId();
    }
  }

}
