CREATE TABLE IF NOT EXISTS claim_reprocess_audit (
    id BIGSERIAL PRIMARY KEY,
    claim_id UUID NOT NULL,
    approved_los INT,
    approved_amount NUMERIC(38, 2),
    remarks VARCHAR(255),
    extra_remarks VARCHAR(255),
    intervention_code VARCHAR(255),
    audit_status VARCHAR(30) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    process_remarks VARCHAR(100),
    CONSTRAINT unique_claim_intervention UNIQUE (claim_id, intervention_code) ---> Unique composite key


);


-- Create a unique index on claim_id
-- Indexes for optimized filtering and lookups
CREATE INDEX IF NOT EXISTS idx_claim_reprocess_audit_processed ON claim_reprocess_audit (processed);
CREATE INDEX IF NOT EXISTS idx_claim_reprocess_audit_processed_at ON claim_reprocess_audit (processed_at);
CREATE INDEX IF NOT EXISTS idx_claim_reprocess_audit_audit_status ON claim_reprocess_audit (audit_status);
CREATE INDEX IF NOT EXISTS idx_claim_reprocess_audit_intervention_code ON claim_reprocess_audit (intervention_code);