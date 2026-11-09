ALTER TABLE claim_items
    ADD COLUMN IF NOT EXISTS shif_amount numeric(15, 2);
