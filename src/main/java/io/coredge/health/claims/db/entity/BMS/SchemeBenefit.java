package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import lombok.*;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;

@Entity
@Table(
        name = "scheme_benefits",
        schema = "public",
        indexes = {
            @Index(name = "idx_sbenefits_scheme_detail_id", columnList = "scheme_detail_id"),
            @Index(name = "idx_sbenefits_benefit_id", columnList = "benefit_id"),
            @Index(name = "idx_sbenefits_status_id", columnList = "status_id"),
            @Index(name = "idx_sbenefits_created_by", columnList = "created_by"),
            @Index(name = "idx_sbenefits_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeBenefit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_detail_id", nullable = false)
    private SchemeDetail schemeDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_id", nullable = false)
    private Benefit benefit;

}
