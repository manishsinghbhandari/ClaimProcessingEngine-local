ALTER TABLE public.intervention_duration
    ADD COLUMN IF NOT EXISTS effective_date timestamp(6) NULL;

ALTER TABLE public.intervention_duration
    ADD COLUMN IF NOT EXISTS allowed_combination text NULL;

ALTER TABLE public.intervention_duration
    ADD COLUMN IF NOT EXISTS unallowed_combination TEXT NULL;