-- Add remarks column to payment_processing table
ALTER TABLE payment_processing ADD COLUMN IF NOT EXISTS remarks TEXT;