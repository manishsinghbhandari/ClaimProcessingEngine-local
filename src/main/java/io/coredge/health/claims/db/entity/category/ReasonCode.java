package io.coredge.health.claims.db.entity.category;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reason_code", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class ReasonCode extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ReasonCategory category;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ReasonCodeStatus status;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean isActive;

}