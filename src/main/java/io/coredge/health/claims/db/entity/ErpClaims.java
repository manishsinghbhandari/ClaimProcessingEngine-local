package io.coredge.health.claims.db.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "erp_claims")
public class ErpClaims {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_code")
    private String vendorCode;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "claim_id")
    private String claimNo;

    @Column(name = "parent_claim_id")
    private String parentClaimNo;

    @Column(name = "has_child", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean hasChild = false;

    @Column(name = "claim_response_id")
    private String claimResponseId;

    @Column(name = "claim_reference_link")
    private String claimReferenceLink;

    @Column(name = "claim_amount")
    private BigDecimal claimAmount;

    @Column(name = "beneficiary_sha_no")
    private String beneficiaryShaNo;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "payment_status")
    private String paymentStatus;

    // Additional Payment-Related Fields
    @Column(name = "type")
    private String type; // Codeable Concept equivalent as String for "Partial or complete payment"

    @Column(name = "adjustment")
    private BigDecimal adjustment; // Payment adjustment for non-claim issues

    @Column(name = "adjustment_reason")
    private String adjustmentReason; // Explanation for the adjustment

    @Temporal(TemporalType.DATE)
    @Column(name = "payment_date")
    private Date date; // Expected date of payment

    @Column(name = "payable_amount")
    private BigDecimal amount; // Payable amount after adjustment

    @Column(name = "identifier")
    private String identifier; // Business identifier for the payment

    @Column(name = "http_call_status")
    private String httpCallStatus; //http code get from ERP side

    @Column(name = "http_call_msg")
    private String httpCallMsg; //http code get from ERP side

    @Column(name = "erp_id")
    private String erpId; //For Success case We get ID in response

    @Column(name = "call_back_status")
    private String callBackStatus; //http code get from ERP side

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request",columnDefinition = "jsonb")
    private String request;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_callback",columnDefinition = "jsonb")
    private String responseCallback;

    @Column(name = "rejection_reason")
    private String rejection_reason;

    @Basic
    @Column(name = "payment_bundle_id")
    private String paymentBundleId;

    @Basic
    @Column(name = "payment_notice_id")
    private String paymentNoticeId;

    @Basic
    @Column(name = "payment_reconciliation_id")
    private String  paymentReconciliationID;

    @Basic
    @Column(name = "remittance_id")
    private String remittanceId;

    @Basic
    @Column(name = "payment_identifier")
    private String paymentIdentifier;

    @Basic
    @Column(name = "payer_invoice_id")
    private String payerInvoiceId;

    @CreatedDate
    @Column(name = "created_date", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    protected LocalDateTime creationDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    protected LocalDateTime lastModifiedDate;

    @Column(name = "process_date")
    private LocalDateTime processDate;

    @Column(name = "is_processed")
    private Boolean isProcessed;

    @Column(name = "status")
    private String status;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "provider_amount")
    private BigDecimal providerAmount;

    @Column(name = "utilization_amount")
    private BigDecimal utilizationAmount;


    @PrePersist
    protected void onCreate() {
        if(creationDate == null) {
            creationDate = LocalDateTime.now();
            lastModifiedDate = LocalDateTime.now();    // Update only when record changes
        }
    }

    @PreUpdate
    protected void onUpdate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();    // Update only when record changes
    }

}
