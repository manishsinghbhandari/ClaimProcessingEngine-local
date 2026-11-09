DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'patient_utilization'
          AND column_name = 'next_availability'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'patient_utilization'
          AND column_name = 'valid_till'
    ) THEN
        ALTER TABLE public.patient_utilization RENAME COLUMN next_availability TO valid_till;
    END IF;
END $$;

DO $$
BEGIN
    -- Add coverage_end_date if it doesn't exist
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'patient'
          AND column_name = 'coverage_end_date'
    ) THEN
        ALTER TABLE public.patient ADD COLUMN coverage_end_date date NULL;
    END IF;

    -- Add coverage_start_date if it doesn't exist
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'patient'
          AND column_name = 'coverage_start_date'
    ) THEN
        ALTER TABLE public.patient ADD COLUMN coverage_start_date date NULL;
    END IF;
END $$;
