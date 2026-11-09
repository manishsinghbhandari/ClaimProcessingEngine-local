-- Add is_workflow flag to claim_ai_trigger table
ALTER TABLE claim_ai_trigger ADD COLUMN IF NOT EXISTS is_workflow BOOLEAN NOT NULL DEFAULT FALSE;
