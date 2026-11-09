package io.coredge.health.claims.db.entity.BMS;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "scheme_members",
        schema = "public",
        indexes = {
            @Index(name = "idx_smembers_scheme_detail_id", columnList = "scheme_detail_id"),
            @Index(name = "idx_smembers_job_group_id", columnList = "job_group_id"),
            @Index(name = "idx_smembers_member_relation_id", columnList = "member_relation_id"),
            @Index(name = "idx_smembers_status_id", columnList = "status_id"),
            @Index(name = "idx_smembers_created_by", columnList = "created_by"),
            @Index(name = "idx_smembers_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeMember extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_detail_id", nullable = false)
    private SchemeDetail schemeDetail;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "member_number", nullable = false)
    private String memberNumber;

    @Column(name = "principal_member_number")
    private String principalMemberNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_group_id")
    private JobGroup jobGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_relation_id")
    private MemberRelation memberRelation;

    @Column(name = "system_code", nullable = true, unique = true)
    private String systemCode;

}
