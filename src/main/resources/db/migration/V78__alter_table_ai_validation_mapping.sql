DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_ai_validation_mapping'
    ) THEN
        ALTER TABLE ai_validation_mapping
        ADD CONSTRAINT uq_ai_validation_mapping UNIQUE (benefit_master_id, ai_validation_id);
    END IF;
END$$;