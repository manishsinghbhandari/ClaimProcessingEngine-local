package io.coredge.health.claims.db.entity.benefits;

import io.coredge.health.claims.db.entity.AuditableBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "benefits_master_log")
@Getter
@Setter
public class BenefitsMasterLog extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benefit_master_id", nullable = false)
    private Long benefitMasterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_record", columnDefinition = "jsonb")
    private String previousRecord;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_record", columnDefinition = "jsonb")
    private String currentRecord;

}