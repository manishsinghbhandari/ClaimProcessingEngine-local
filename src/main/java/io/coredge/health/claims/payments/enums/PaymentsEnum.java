package io.coredge.health.claims.payments.enums;

public class PaymentsEnum {

    private PaymentsEnum() {
        throw new IllegalStateException("Utility class");
    }

    public enum PaymentStatus {
        SENT_TO_ERP,
        APPROVED_FOR_PAYMENT,
        SENT_FOR_PAYMENT,
        PAYMENT_COMPLETED,
        PAYMENT_REJECTED,
        NEEDS_REPARATION,
        TABLE_NEEDS_REPARATION,
        PAYMENT_NEEDS_REPARATION,
        ERP_PAYMENT_NEEDS_REPARATION

    }

    public enum AuditStatus {
        PASSED,
        FAILED
    }

    public enum BatchStatus {
        CREATED,
        SENT_FOR_PAYMENT,
        PAYMENT_COMPLETED,
        PAYMENT_REJECTED,
        FAILED
    }

    public enum ErpType {
        IFS,
        ODOO,
        CPP
    }

    public enum AuditEventType {
        CLAIM,
        BATCH,
        PAYMENT_UPDATE
    }

    public enum SystemType {
        ERP,
        CPP,
        IFS,
        ODOO,
        PAYER,
        PAYMENT_GATEWAY
    }
}