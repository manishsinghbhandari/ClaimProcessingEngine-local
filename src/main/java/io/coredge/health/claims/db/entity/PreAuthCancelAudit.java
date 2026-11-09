package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "preauth_cancel_audit", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthCancelAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "source")
    private String source;

    @Column(name = "requester")
    private String requester;

    // JSONB as String (Hibernate 6+ supports JSON mapping with custom types, but String is easiest)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", columnDefinition = "jsonb")
    private String requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", columnDefinition = "jsonb")
    private String response;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "status")
    private String status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date", updatable = false, insertable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
}
