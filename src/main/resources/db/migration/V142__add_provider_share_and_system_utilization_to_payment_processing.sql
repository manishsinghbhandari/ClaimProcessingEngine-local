-- Add provider_share_amount and system_utilization_amount columns to payment_processing and payment_batch tables
ALTER TABLE payment_processing ADD COLUMN IF NOT EXISTS provider_share_amount DECIMAL(19,4);
ALTER TABLE payment_processing ADD COLUMN IF NOT EXISTS system_utilization_amount DECIMAL(19,4);

ALTER TABLE payment_batch ADD COLUMN IF NOT EXISTS provider_share_amount DECIMAL(19,4);
ALTER TABLE payment_batch ADD COLUMN IF NOT EXISTS system_utilization_amount DECIMAL(19,4);
