DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'intervention_code'
              AND column_name = 'code_combination'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'intervention_code'
              AND column_name = 'allowed_combination'
        ) THEN
            ALTER TABLE public.intervention_code RENAME COLUMN code_combination TO allowed_combination;
        END IF;
    END
$$;

-- Add column 'unallowed_combination' if it does not exist
ALTER TABLE public.intervention_code
    ADD COLUMN IF NOT EXISTS unallowed_combination TEXT NULL;
