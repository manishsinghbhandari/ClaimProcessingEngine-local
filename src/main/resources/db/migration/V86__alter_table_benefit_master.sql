DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'benefits_master'
          AND column_name = 'cycle_limit_in_day'
    ) THEN
        ALTER TABLE public.benefits_master
        ALTER COLUMN cycle_limit_in_day DROP NOT NULL;
    END IF;
END $$;