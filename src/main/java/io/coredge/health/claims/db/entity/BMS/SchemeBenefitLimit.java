package io.coredge.health.claims.db.entity.BMS;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "scheme_benefit_limits",
        schema = "public",
        indexes = {
            @Index(name = "idx_sblimit_scheme_detail_id", columnList = "scheme_detail_id"),
            @Index(name = "idx_sblimit_benefit_id", columnList = "benefit_id"),
            @Index(name = "idx_sblimit_job_group_id", columnList = "job_group_id"),
            @Index(name = "idx_sblimit_status_id", columnList = "status_id"),
            @Index(name = "idx_sblimit_created_by", columnList = "created_by"),
            @Index(name = "idx_sblimit_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeBenefitLimit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_detail_id", nullable = false)
    private SchemeDetail schemeDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_id", nullable = false)
    private Benefit benefit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_group_id")
    private JobGroup jobGroup;

    @Column(name = "is_limit_shared")
    private Boolean isLimitShared;

    @Column(name = "is_limited")
    private Boolean isLimited;

    @Column(name = "limit_amount")
    private Double limitAmount;

}
