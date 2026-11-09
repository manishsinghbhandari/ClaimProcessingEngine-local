-- Add phc_batch_payment_id FK and approved/paid amounts to phc_claim_payments
ALTER TABLE public.phc_claim_payments
    ADD COLUMN IF NOT EXISTS phc_batch_payment_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS approved_amount NUMERIC(38,2),
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(38,2);

-- Foreign key to phc_batch_payments (use DO block because ADD CONSTRAINT ... IF NOT EXISTS is not supported)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'phc_claim_payments_batch_fk'
    ) THEN
        ALTER TABLE public.phc_claim_payments
            ADD CONSTRAINT phc_claim_payments_batch_fk
            FOREIGN KEY (phc_batch_payment_id)
            REFERENCES public.phc_batch_payments(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Helpful index for joins on the FK
CREATE INDEX IF NOT EXISTS idx_phc_claim_payments_batch_id ON public.phc_claim_payments (phc_batch_payment_id);
