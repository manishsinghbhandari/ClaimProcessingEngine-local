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
@Table(name = "civil_servant_audit")
@EqualsAndHashCode(callSuper = true)
public class CivilServantClaimsAudit extends AuditableBase<String>{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "status")
    private String status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "intervention_codes", columnDefinition = "varchar[]")
    private List<String> interventionCodes;

    @Column(name = "response_message")
    private String responseMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body",columnDefinition = "jsonb")
    private String requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response",columnDefinition = "jsonb")
    private String responseBody;

}
