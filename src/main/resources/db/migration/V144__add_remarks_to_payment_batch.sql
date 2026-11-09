-- Add remarks column to payment_batch table
ALTER TABLE payment_batch ADD COLUMN IF NOT EXISTS remarks TEXT;
