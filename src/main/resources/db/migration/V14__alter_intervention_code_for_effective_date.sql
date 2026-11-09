DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'intervention_code'
              AND column_name = 'effective_date'
        ) THEN
            ALTER TABLE public.intervention_code
                ADD COLUMN effective_date timestamp(6) NULL;
        END IF;
    END$$;

UPDATE public.intervention_code
SET effective_date='2025-07-02 00:00:00.000' where effective_date is null;

ALTER TABLE public.intervention_code ALTER COLUMN effective_date SET NOT NULL;
