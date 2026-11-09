DO $$
BEGIN
    -- Only attempt the alter if:
    -- A. The table exists
    -- B. The column exists
    -- C. The column is NOT already 'text' (prevents redundant locks)
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'process_notes'
          AND column_name = 'notes'
          AND data_type != 'text'
    ) THEN
        -- Execute the alteration
        ALTER TABLE public.process_notes
        ALTER COLUMN notes TYPE TEXT;
    END IF;

END $$;