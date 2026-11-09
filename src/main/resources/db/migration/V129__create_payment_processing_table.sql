CREATE TABLE IF NOT EXISTS payment_processing (
    id BIGSERIAL PRIMARY KEY,

    claim_id VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128),
    amount DECIMAL(19,4),
    claim_creation_date TIMESTAMP,
    patient_id VARCHAR(64),
    fund_scheme VARCHAR(64),
    ceo_approval_date TIMESTAMP,
    payment_completion_time TIMESTAMP,

    erp_type VARCHAR(20) NOT NULL, -- IFS / ODOO

    payment_status VARCHAR(30) NOT NULL,

    batch_id VARCHAR(64),

    -- Bank reference from payment gateway
    bank_reference_number VARCHAR(128),

    debit_bank_account VARCHAR(128),
    debit_bank_name VARCHAR(128),

    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,

    CONSTRAINT uk_payment_claim UNIQUE (claim_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_status
    ON payment_processing (payment_status);

CREATE INDEX IF NOT EXISTS idx_payment_batch
    ON payment_processing (batch_id);