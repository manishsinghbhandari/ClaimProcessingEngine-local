package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.enums.AssignmentFilterType;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cascade;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_bucket", schema = "public")
public class UserBucket extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @Cascade(org.hibernate.annotations.CascadeType.SAVE_UPDATE)
    @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_id")
    private InsuranceClaim claim;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ClaimType.BucketStatus status;

    @Column(name = "claim_status")
    @Enumerated(EnumType.STRING)
    private ExtensionStateCodeEnum claimStatus;

    @Column(name = "assigned_date")
    private LocalDateTime assignedDate;

    @Column(name = "action_date")
    private LocalDateTime actionDate;

    @Column(name = "claim_type")
    @Enumerated(EnumType.STRING)
    private ClaimType claimType;


    @Column(name = "filter_type")
    @Enumerated(EnumType.STRING)
    private AssignmentFilterType assignmentFilterType;

    @PrePersist
    public void prePersist() {
        // Set the auditableData.
        if (StringUtils.isNotEmpty(HealthIdContextHolder.userId())) {
            this.createdBy = HealthIdContextHolder.userId();
            this.lastModifiedBy = HealthIdContextHolder.userId();
        }
    }

}
