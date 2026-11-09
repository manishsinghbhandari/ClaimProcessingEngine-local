package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "global_property")
@EqualsAndHashCode(callSuper = true)
public class GlobalProperty extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property", nullable = false, updatable = false)
    private String property;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "status", nullable = false)
    private Boolean status;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

}
