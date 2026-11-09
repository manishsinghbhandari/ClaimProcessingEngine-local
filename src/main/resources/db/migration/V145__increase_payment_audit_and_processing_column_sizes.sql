-- Increase column sizes for robustness against long gateway responses and descriptive statuses

-- Payment Audit Table
ALTER TABLE payment_audit ALTER COLUMN response_status TYPE VARCHAR(255);
ALTER TABLE payment_audit ALTER COLUMN bank_reference_number TYPE TEXT;
ALTER TABLE payment_audit ALTER COLUMN forward_status TYPE VARCHAR(100);
ALTER TABLE payment_audit ALTER COLUMN claim_id TYPE VARCHAR(100);
ALTER TABLE payment_audit ALTER COLUMN batch_id TYPE VARCHAR(100);

-- Payment Processing Table
ALTER TABLE payment_processing ALTER COLUMN bank_reference_number TYPE TEXT;
ALTER TABLE payment_processing ALTER COLUMN claim_id TYPE VARCHAR(100);
ALTER TABLE payment_processing ALTER COLUMN batch_id TYPE VARCHAR(100);
