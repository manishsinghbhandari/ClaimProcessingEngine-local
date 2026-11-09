package io.coredge.health.claims.payments.entity;

import io.coredge.health.claims.payments.enums.PaymentsEnum;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_audit")
@Data
public class PaymentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private PaymentsEnum.AuditEventType eventType;

    private String claimId;
    private String batchId;
    private String providerId;

    @Enumerated(EnumType.STRING)
    private PaymentsEnum.SystemType sourceSystem;

    @Enumerated(EnumType.STRING)
    private PaymentsEnum.SystemType targetSystem;

    @Enumerated(EnumType.STRING)
    private PaymentsEnum.PaymentStatus forwardStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    private String responseStatus;

    private String bankReferenceNumber;

    private String status; // Passed / Failed
    private String remarks;

}
