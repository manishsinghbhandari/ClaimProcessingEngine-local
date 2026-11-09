-- Add status and remarks columns to payment_audit
ALTER TABLE payment_audit ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE payment_audit ADD COLUMN IF NOT EXISTS remarks TEXT;
