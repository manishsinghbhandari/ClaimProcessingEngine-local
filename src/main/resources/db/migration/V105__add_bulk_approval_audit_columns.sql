-- Add columns for bulk approval tracking to claim_reprocess_audit table
ALTER TABLE claim_reprocess_audit
ADD COLUMN IF NOT EXISTS approver_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS bulk_processing_id VARCHAR(100);

-- Index for efficient lookup of bulk operations
CREATE INDEX IF NOT EXISTS idx_claim_reprocess_audit_bulk_id 
    ON claim_reprocess_audit(bulk_processing_id);

COMMENT ON COLUMN claim_reprocess_audit.approver_name IS 'Name of the user who approved the claim in bulk operation';
COMMENT ON COLUMN claim_reprocess_audit.bulk_processing_id IS 'Unique ID linking all records from the same bulk approval operation';
