package io.coredge.health.claims.db.entity.BMS;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "scheme_member_limits",
        schema = "public",
        indexes = {
            @Index(name = "idx_smlimits_scheme_detail_id", columnList = "scheme_detail_id"),
            @Index(name = "idx_smlimits_scheme_member_id", columnList = "scheme_member_id"),
            @Index(name = "idx_smlimits_benefit_id", columnList = "benefit_id"),
            @Index(name = "idx_smlimits_status_id", columnList = "status_id"),
            @Index(name = "idx_smlimits_created_by", columnList = "created_by"),
            @Index(name = "idx_smlimits_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeMemberLimit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_detail_id", nullable = false)
    private SchemeDetail schemeDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_member_id", nullable = false)
    private SchemeMember schemeMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_id", nullable = false)
    private Benefit benefit;

    @Column(name = "is_limit_shared", nullable = false)
    private Boolean isLimitShared;

    @Column(name = "is_limited", nullable = false)
    private Boolean isLimited;

    @Column(name = "limit_amount", nullable = false)
    private Double limitAmount;

    @Column(name = "limit_utilized")
    private Double limitUtilized;

    @Column(name = "limit_over_utilized")
    private Double limitOverUtilized;

    @Column(name = "limit_balance")
    private Double limitBalance;
}
