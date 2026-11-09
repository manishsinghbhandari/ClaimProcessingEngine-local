-- Safely add the new column
ALTER TABLE payment_processing ADD COLUMN IF NOT EXISTS payment_processing_remarks VARCHAR(500);