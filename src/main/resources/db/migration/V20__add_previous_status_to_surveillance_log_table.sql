-- Add previous_status column to surveillance_log table
ALTER TABLE surveillance_log ADD COLUMN IF NOT EXISTS previous_status varchar(50) NULL;