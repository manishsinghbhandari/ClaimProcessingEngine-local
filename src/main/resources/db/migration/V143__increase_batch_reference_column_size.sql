-- Increase column size for gateway and bank references to TEXT to allow for JSON or long references
ALTER TABLE payment_batch ALTER COLUMN payment_gateway_reference TYPE TEXT;
ALTER TABLE payment_batch ALTER COLUMN bank_reference_number TYPE TEXT;
