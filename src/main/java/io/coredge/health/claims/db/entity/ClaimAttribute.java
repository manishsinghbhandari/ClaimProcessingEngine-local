package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.AttributeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Entity
@Table(name = "claim_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimAttribute extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttributeStatus status;

    @Column(name = "poc")
    private String pointOfCare;

    @Column(name = "coverage_type")
    private String coverageType;

    @Column(name = "employer")
    private String employer;

    @Column(name = "job_group")
    private String jobGroup;
}
