DO
$$
BEGIN
    -- Check if column 'scores' already exists in 'claim_ai_notes' table
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'claim_ai_notes'
          AND column_name = 'scores'
    ) THEN
        ALTER TABLE public.claim_ai_notes ADD COLUMN scores jsonb NOT NULL DEFAULT '{}'::jsonb;
    END IF;
END
$$;