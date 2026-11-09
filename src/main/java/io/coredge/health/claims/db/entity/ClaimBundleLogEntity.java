package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "claim_bundle_log", schema = "public")
public class ClaimBundleLogEntity extends AuditableBase<String> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "claim_bundle_log_id")
  private Long id;

  @Column(name = "claim_id")
  private UUID claimId;

  @Column(name = "request_json")
  private String requestJson;

  @Column(name = "version")
  private String version;
}
