-- PHC Processing Cursor Table
-- Tracks background processing of PHC calculations, approvals, and ERP submissions
-- Enables resumable batch processing with checkpoints

CREATE TABLE IF NOT EXISTS phc_processing_cursor (
    id BIGSERIAL PRIMARY KEY,
    year_month VARCHAR(10) NOT NULL,
    batch_preparation_date DATE NOT NULL,
    action VARCHAR(20) NOT NULL,
    last_processed_batch_id BIGINT,
    last_processed_payment_id BIGINT,
    total_records INTEGER DEFAULT 0,
    processed_records INTEGER DEFAULT 0,
    failed_records INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Audit fields
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_phc_cursor_year_month_action_status UNIQUE (year_month, action, status),
    CONSTRAINT chk_phc_cursor_action CHECK (action IN ('CALCULATION', 'APPROVAL', 'SEND_TO_ERP')),
    CONSTRAINT chk_phc_cursor_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PAUSED'))
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_phc_cursor_status_action_status ON phc_processing_cursor(status, action, status);
CREATE INDEX IF NOT EXISTS idx_phc_cursor_year_month ON phc_processing_cursor(year_month);
CREATE INDEX IF NOT EXISTS idx_phc_cursor_status ON phc_processing_cursor(status);