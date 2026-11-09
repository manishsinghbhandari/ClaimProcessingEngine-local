-- Table: public.patient

ALTER TABLE patient
    ADD COLUMN IF NOT EXISTS member_number VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS principal_member_number VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS job_group_id INTEGER NULL,
    ADD COLUMN IF NOT EXISTS member_relation_id INTEGER NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_job_group'
          AND table_name = 'patient'
    ) THEN
        ALTER TABLE patient
        ADD CONSTRAINT fk_job_group FOREIGN KEY (job_group_id) REFERENCES job_groups(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_member_relation'
          AND table_name = 'patient'
    ) THEN
        ALTER TABLE patient
        ADD CONSTRAINT fk_member_relation FOREIGN KEY (member_relation_id) REFERENCES member_relations(id);
    END IF;
END $$;

