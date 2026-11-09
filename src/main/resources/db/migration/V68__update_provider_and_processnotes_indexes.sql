-- =================================================
-- Migration: Update Provider & ProcessNotes indexes
-- =================================================

-- 1. Remove index on provider.mfl_code if exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'provider_mfl_code'
    ) THEN
        EXECUTE 'DROP INDEX public.provider_mfl_code';
    END IF;
END$$;

-- 2. Add index on provider.facility_level if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_provider_level'
    ) THEN
        EXECUTE 'CREATE INDEX idx_provider_level ON public.provider (level)';
    END IF;
END$$;

-- 3. Add index on process_notes.claim_id if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename  = 'process_notes'
          AND indexname  = 'idx_process_notes_claim_id'
    ) THEN
        EXECUTE 'CREATE INDEX idx_process_notes_claim_id ON public.process_notes (claim_id)';
    END IF;
END$$;


-- 4. Add index on phc_claim_payments.claim_id if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'phc_claim_payments'
          AND indexname = 'idx_phc_claim_payments_aggregated_claim_id'
    ) THEN
        EXECUTE 'CREATE INDEX idx_phc_claim_payments_aggregated_claim_id ON public.phc_claim_payments (aggregated_claim_id)';
    END IF;
END $$;
