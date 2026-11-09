package io.coredge.health.claims.payments.entity;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "payment_batch")
@Data
@EqualsAndHashCode(callSuper = false)
public class PaymentBatch extends AuditableBase<String> {

    @Id
    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_completion_time")
    private java.time.LocalDateTime paymentCompletionTime;

    @Column(name = "claim_ids")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> claimIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false)
    private PaymentsEnum.BatchStatus batchStatus;

    @Column(name = "payment_gateway_reference", columnDefinition = "TEXT")
    private String paymentGatewayReference;

    @Column(name = "bank_reference_number", columnDefinition = "TEXT")
    private String bankReferenceNumber;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "purchase_order_number")
    private String purchaseOrderNumber;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "provider_share_amount")
    private BigDecimal providerShareAmount;

    @Column(name = "system_utilization_amount")
    private BigDecimal systemUtilizationAmount;

}
