package io.coredge.health.claims.db.entity.permission;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.enums.UserStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "audit_user_status")
public class AuditUserStatus extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "previous_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatusEnum previousStatus;

    @Column(name = "new_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatusEnum newStatus;

    @Column(name = "action", nullable = false)
    private String action;

    @PrePersist
    private void prePersist() {
        this.lastModifiedBy = HealthIdContextHolder.userId();
    }
}
