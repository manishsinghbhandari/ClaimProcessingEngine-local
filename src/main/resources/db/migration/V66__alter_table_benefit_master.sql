DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'benefits_master'
          AND column_name = 'cycle_limit_in_day'
    ) THEN
        ALTER TABLE public.benefits_master
        ADD COLUMN cycle_limit_in_day int4 DEFAULT NULL;
    END IF;
END $$;