-- ============================================
-- Add auditing columns to phc_claim_payments
-- ============================================
ALTER TABLE public.phc_claim_payments
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_modified_date TIMESTAMP WITHOUT TIME ZONE;


-- ============================================
-- Add auditing columns to phc_batch_payments
-- (Because entity PhcBatchPayment also extends AuditableBase)
-- ============================================
ALTER TABLE public.phc_batch_payments
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_modified_date TIMESTAMP WITHOUT TIME ZONE;
