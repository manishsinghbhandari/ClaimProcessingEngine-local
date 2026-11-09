package io.coredge.health.claims.payments.utils;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PaymentIdGenerator {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public PaymentIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private Long getNextSequenceValue() {
        return jdbcTemplate.queryForObject("SELECT nextval('payment_id_seq')", Long.class);
    }

    public String generateInvoiceNumber() {
        return "INV-" + LocalDateTime.now().format(DATE_FORMATTER) + "-" + getNextSequenceValue();
    }

    public String generateBatchId() {
        return "BATCH-" + String.format("%08X", getNextSequenceValue());
    }

    public String generatePurchaseOrderNumber() {
        return "PO-" + LocalDateTime.now().format(DATE_FORMATTER) + "-" + getNextSequenceValue();
    }
}
