-- Add surveillance_status column to claims table
ALTER TABLE claims ADD COLUMN IF NOT EXISTS surveillance_status varchar(255) NULL;