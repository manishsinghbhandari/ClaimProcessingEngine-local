package io.coredge.health.claims.db.entity.AI;

import io.coredge.health.claims.db.entity.AuditableBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_validation_log")
@Getter
@Setter
public class AIValidationLog extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_validation_id", nullable = false)
    private Long aiValidationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_record", columnDefinition = "jsonb")
    private String previousRecord;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_record", columnDefinition = "jsonb")
    private String currentRecord;


}
