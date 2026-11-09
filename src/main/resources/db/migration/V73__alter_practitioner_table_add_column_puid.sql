DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'm_practitioner_details'
              AND column_name = 'pu_id'
        ) THEN
            ALTER TABLE public.m_practitioner_details ADD COLUMN pu_id VARCHAR(100);
            ALTER TABLE public.m_practitioner_details ADD CONSTRAINT unique_pu_id UNIQUE (pu_id);
        END IF;
    END
$$;