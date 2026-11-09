-- Alter phc_claim_payments table to change remarks column type to TEXT
ALTER TABLE phc_claim_payments
    ALTER COLUMN remarks TYPE TEXT;
