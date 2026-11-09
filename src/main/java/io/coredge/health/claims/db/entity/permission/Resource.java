package io.coredge.health.claims.db.entity.permission;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "resources")
public class Resource  extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceEnum name;

    @PrePersist
    private void prePersist(){
        this.createdBy = HealthIdContextHolder.userId();
        this.lastModifiedBy = HealthIdContextHolder.userId();
    }
}
