CREATE TABLE IF NOT EXISTS batch_approval_request (
    id                BIGSERIAL    PRIMARY KEY,
    batch_status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    total_claims      INTEGER      NOT NULL DEFAULT 0,
    successful        INTEGER      NOT NULL DEFAULT 0,
    failed            INTEGER      NOT NULL DEFAULT 0,
    initiated_by      VARCHAR(100) NOT NULL,
    intervention_code VARCHAR(50)  NOT NULL,
    period_start      DATE         NOT NULL,
    period_end        DATE         NOT NULL,
    status_filter     VARCHAR(30),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Index on batch_status for scheduler/monitoring queries
CREATE INDEX IF NOT EXISTS idx_batch_approval_status ON batch_approval_request(batch_status);
