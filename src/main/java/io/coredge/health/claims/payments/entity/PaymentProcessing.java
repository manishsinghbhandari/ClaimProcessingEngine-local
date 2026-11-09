package io.coredge.health.claims.payments.entity;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.models.eclaims.Invoice;
import io.coredge.health.claims.payments.utils.JpaCryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Entity
@Table(name = "payment_processing")
@Getter
@Setter
public class PaymentProcessing extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String claimId;
    private String parentId;
    private String providerId;
    private String providerName;
    private BigDecimal amount;
    private LocalDateTime claimCreationDate;
    private String patientId;
    private String fundScheme;
    private LocalDateTime ceoApprovalDate;
    private LocalDateTime paymentCompletionTime;
    private String erpType;
    private String paymentStatus;
    private String batchId;
    private String bankReferenceNumber;
    private String remarks;

    @Column(name = "provider_share_amount")
    private BigDecimal providerShareAmount;

    @Column(name = "system_utilization_amount")
    private BigDecimal systemUtilizationAmount;

    // Encrypt before storing
    @Column(name = "debit_bank_account")
    @Convert(converter = JpaCryptoConverter.class)
    private String debitBankAccount;

    @Column(name = "debit_bank_name")
    @Convert(converter = JpaCryptoConverter.class)
    private String debitBankName;

}
