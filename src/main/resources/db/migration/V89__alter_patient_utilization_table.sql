-- Add new columns if not exist
ALTER TABLE public.patient_utilization
    ADD COLUMN IF NOT EXISTS individual_max_during_period INT4 NULL,
    ADD COLUMN IF NOT EXISTS individual_utilised_during_period INT4 NULL,
    ADD COLUMN IF NOT EXISTS last_usage_date DATE NULL,
    ADD COLUMN IF NOT EXISTS utilization_period VARCHAR(255) NULL;

-- Add check constraint (create only if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'patient_utilization_utilization_period_check'
    ) THEN
        ALTER TABLE public.patient_utilization
        ADD CONSTRAINT patient_utilization_utilization_period_check
        CHECK (
            utilization_period::text = ANY (
                ARRAY[
                    'DAILY'::varchar,
                    'WEEKLY'::varchar,
                    'MONTHLY'::varchar,
                    'QUARTERLY'::varchar,
                    'MATERNITY'::varchar,
                    'YEARLY'::varchar
                ]::text[]
            )
        );
    END IF;
END$$;