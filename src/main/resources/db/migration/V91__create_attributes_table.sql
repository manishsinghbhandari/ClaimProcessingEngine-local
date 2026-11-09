CREATE TABLE IF NOT EXISTS claim_attributes (
    id BIGSERIAL PRIMARY KEY,
    claim_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('VOIDED', 'RETIRED', 'ACTIVE')),
    poc VARCHAR(255),
    coverage_type VARCHAR(255),
    employer VARCHAR(255),
    job_group VARCHAR(255),
    created_by VARCHAR(255),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,

    CONSTRAINT fk_claim
        FOREIGN KEY (claim_id) REFERENCES claims(claim_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_claim_id ON claim_attributes (claim_id);
CREATE INDEX IF NOT EXISTS idx_status ON claim_attributes (status);
