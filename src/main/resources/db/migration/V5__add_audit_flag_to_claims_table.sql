-- Add audit_flag column to claims table
ALTER TABLE claims ADD COLUMN IF NOT EXISTS audit_flag BOOLEAN DEFAULT FALSE;
