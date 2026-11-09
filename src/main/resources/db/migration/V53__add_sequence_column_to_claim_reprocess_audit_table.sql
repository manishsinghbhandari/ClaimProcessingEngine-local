ALTER TABLE public.claim_reprocess_audit
    ADD COLUMN IF NOT EXISTS sequence INT NULL;