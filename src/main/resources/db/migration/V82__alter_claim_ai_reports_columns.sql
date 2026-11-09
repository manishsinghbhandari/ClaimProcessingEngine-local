-- Alter claim_ai_reports table to use JSONB for notes and scores columns
-- Add FAILED status to constraint check

-- Change notes column from TEXT to JSONB
ALTER TABLE public.claim_ai_reports 
ALTER COLUMN notes TYPE JSONB USING notes::jsonb;

-- Change scores column from DECIMAL to JSONB
ALTER TABLE public.claim_ai_reports 
ALTER COLUMN scores TYPE JSONB USING 
    CASE 
        WHEN scores IS NULL THEN NULL
        ELSE jsonb_build_object('value', scores)
    END;

-- Update constraint to include FAILED status
ALTER TABLE public.claim_ai_reports 
DROP CONSTRAINT IF EXISTS valid_report_status;

ALTER TABLE public.claim_ai_reports 
ADD CONSTRAINT valid_report_status CHECK (status IN ('PENDING', 'REVIEWED', 'FLAGGED', 'SUCCESS', 'FAILED'));
