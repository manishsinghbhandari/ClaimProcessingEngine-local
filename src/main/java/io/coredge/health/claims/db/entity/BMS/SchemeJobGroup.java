package io.coredge.health.claims.db.entity.BMS;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "scheme_job_groups",
        schema = "public",
        indexes = {
            @Index(name = "idx_sjgroups_scheme_detail_id", columnList = "scheme_detail_id"),
            @Index(name = "idx_sjgroups_job_group_id", columnList = "job_group_id"),
            @Index(name = "idx_sjgroups_status_id", columnList = "status_id"),
            @Index(name = "idx_sjgroups_created_by", columnList = "created_by"),
            @Index(name = "idx_sjgroups_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeJobGroup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_detail_id", nullable = false)
    private SchemeDetail schemeDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_group_id", nullable = false)
    private JobGroup jobGroup;
}
