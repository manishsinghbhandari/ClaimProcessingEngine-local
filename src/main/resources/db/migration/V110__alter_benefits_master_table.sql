-- Add column if not present
ALTER TABLE public.benefits_master
ADD COLUMN IF NOT EXISTS utilization_period varchar(20) DEFAULT 'YEARLY' NOT NULL;

ALTER TABLE public.benefits_master ADD COLUMN IF NOT EXISTS period_count int4 NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'benefits_master_utilization_period_check'
    ) THEN
        ALTER TABLE public.benefits_master
        ADD CONSTRAINT benefits_master_utilization_period_check
        CHECK (
            utilization_period::text = ANY (
                ARRAY[
                    'YEARLY',
                    'MONTHLY',
                    'WEEKLY',
                    'DAILY',
                    'QUARTERLY',
                    'TWO_YEARLY',
                    'NO_LIMIT',
                    'MATERNITY'
                ]::text[]
            )
        );
    END IF;
END $$;



UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'TWO_YEARLY'
WHERE
    intervention_code = 'SHA-05-001';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'YEARLY'
WHERE
    intervention_code = 'SHA-08-004';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MATERNITY'
WHERE
    intervention_code = 'SHA-08-005';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MATERNITY'
WHERE
    intervention_code = 'SHA-08-006';


UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-001';

UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-002';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MONTHLY'
WHERE
    intervention_code = 'SHA-16-004';


UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-007';

UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'MONTHLY'
WHERE
    intervention_code = 'SHA-16-011';



UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'TWO_YEARLY'
WHERE
    intervention_code = 'SHA-05-001';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'YEARLY'
WHERE
    intervention_code = 'SHA-08-004';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MATERNITY'
WHERE
    intervention_code = 'SHA-08-005';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MATERNITY'
WHERE
    intervention_code = 'SHA-08-006';


UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-001';

UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-002';


UPDATE benefits_master
SET
    period_count = 1,
    utilization_period = 'MONTHLY'
WHERE
    intervention_code = 'SHA-16-004';


UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'WEEKLY'
WHERE
    intervention_code = 'SHA-16-007';

UPDATE benefits_master
SET
    period_count = 2,
    utilization_period = 'MONTHLY'
WHERE
    intervention_code = 'SHA-16-011';
