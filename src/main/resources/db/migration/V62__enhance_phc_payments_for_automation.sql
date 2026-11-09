-- Add approval workflow fields to phc_batch_payments table
ALTER TABLE phc_batch_payments 
ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);

-- Add approval workflow fields to phc_claim_payments table
ALTER TABLE phc_claim_payments 
ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);

-- Add indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_phc_batch_payments_status ON phc_batch_payments(status);
CREATE INDEX IF NOT EXISTS idx_phc_batch_payments_date ON phc_batch_payments(date);
CREATE INDEX IF NOT EXISTS idx_phc_batch_payments_approved_by ON phc_batch_payments(approved_by);

CREATE INDEX IF NOT EXISTS idx_phc_claim_payments_status ON phc_claim_payments(status);
CREATE INDEX IF NOT EXISTS idx_phc_claim_payments_provider_id ON phc_claim_payments(provider_id);
CREATE INDEX IF NOT EXISTS idx_phc_claim_payments_batch_id ON phc_claim_payments(phc_batch_payment_id);