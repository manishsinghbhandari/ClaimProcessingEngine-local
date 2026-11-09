-- Add phc_claim_payments_id to claims with FK to phc_claim_payments(id)
ALTER TABLE public.claims
    ADD COLUMN IF NOT EXISTS phc_claim_payments_id BIGINT NULL;

-- Add FK constraint safely (PostgreSQL doesn't support IF NOT EXISTS on constraints)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'claims_phc_claim_payments_fk'
    ) THEN
        ALTER TABLE public.claims
            ADD CONSTRAINT claims_phc_claim_payments_fk
            FOREIGN KEY (phc_claim_payments_id)
            REFERENCES public.phc_claim_payments(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Helpful index for joins on the FK
CREATE INDEX IF NOT EXISTS idx_claims_phc_claim_payments_id ON public.claims (phc_claim_payments_id);

