package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@Table(name = "diagnosis", schema = "public")
public class Diagnosis extends AuditableBase<String> {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Basic
  @Column(name = "code", unique = false, nullable = true)
  private String code;

  @Basic
  @Column(name = "name", unique = false, nullable = true)
  private String name;

  @Basic
  @Column(name = "patient_id", unique = false, nullable = false)
  private String patientId;

  @Basic
  @Column(name = "claim_id", unique = false, nullable = false)
  private String claimId;

  @Basic
  @Column(name = "status", unique = false, nullable = false)
  private String status;
}
