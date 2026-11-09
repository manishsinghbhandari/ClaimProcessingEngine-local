-- Add parent_id column to payment_processing table for hierarchical relationships
ALTER TABLE payment_processing
ADD COLUMN IF NOT EXISTS parent_id VARCHAR(50);

-- Create index for parent_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_payment_processing_parent_id
ON payment_processing (parent_id);
