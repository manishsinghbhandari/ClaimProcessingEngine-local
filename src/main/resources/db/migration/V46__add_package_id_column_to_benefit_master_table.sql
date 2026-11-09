ALTER TABLE public.benefits_master
    ADD COLUMN IF NOT EXISTS package_master_id INT NULL;