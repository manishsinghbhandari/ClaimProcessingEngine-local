-- Safely update CHECK constraint on user_bucket.status

-- 1. Drop existing constraint ONLY if it exists
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'user_bucket'::regclass
      AND conname = 'user_bucket_status_check';

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE user_bucket DROP CONSTRAINT ' || constraint_name || ';';
    END IF;
END$$;


-- 2. Add the corrected CHECK constraint ONLY if it does not already exist
DO $$
DECLARE
    constraint_exists boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'user_bucket'::regclass
          AND conname = 'user_bucket_status_check'
    ) INTO constraint_exists;

    IF NOT constraint_exists THEN
        EXECUTE '
            ALTER TABLE user_bucket 
            ADD CONSTRAINT user_bucket_status_check
            CHECK (status IN (''PENDING'', ''COMPLETED'', ''RELEASED''))
        ';
    END IF;
END$$;


