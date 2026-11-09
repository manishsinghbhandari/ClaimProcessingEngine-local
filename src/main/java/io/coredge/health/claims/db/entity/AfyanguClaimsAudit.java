package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "afyangu_audit")
@EqualsAndHashCode(callSuper = true)
public class AfyanguClaimsAudit extends AuditableBase<String>{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "status")
    private String status;

    @Column(name = "claim_status")
    private String claimStatus;

    @Column(name = "reference_no")
    private String referenceNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body",columnDefinition = "jsonb")
    private String requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response",columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "identifier_type")
    private String identifierType;

    @Column(name = "use")
    private String use;
}
