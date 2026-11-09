CREATE TABLE IF NOT EXISTS payment_audit (
    audit_id BIGSERIAL PRIMARY KEY,

    event_type VARCHAR(20) NOT NULL, -- CLAIM / BATCH

    claim_id VARCHAR(64),
    batch_id VARCHAR(64),
    provider_id VARCHAR(64),

    source_system VARCHAR(30) NOT NULL,
    target_system VARCHAR(30) NOT NULL,

    forward_status VARCHAR(30) NOT NULL,

    request_payload JSONB,
    response_payload JSONB,

    response_status VARCHAR(30),

    bank_reference_number VARCHAR(128),

    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT chk_claim_or_batch
        CHECK (
            (claim_id IS NOT NULL)
            OR (batch_id IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_audit_claim
    ON payment_audit (claim_id);

CREATE INDEX IF NOT EXISTS idx_audit_batch
    ON payment_audit (batch_id);
