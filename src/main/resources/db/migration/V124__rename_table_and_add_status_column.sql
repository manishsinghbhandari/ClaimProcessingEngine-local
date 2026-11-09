-- 1 Rename tables first
ALTER TABLE IF EXISTS rejection_code RENAME TO reason_code;
ALTER TABLE IF EXISTS rejection_category RENAME TO reason_category;


-- 2 Create ENUM type (only if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'status_enum'
    ) THEN
CREATE TYPE status_enum AS ENUM (
            'APPROVED',
            'REJECTED',
            'SENT_BACK',
            'SENT_TO_SURVEILLANCE',
            'MISSING_DOCUMENTS'
        );
END IF;
END $$;


-- 3 Add nullable column using ENUM type
ALTER TABLE IF EXISTS reason_code
    ADD COLUMN IF NOT EXISTS status status_enum;