-- Alter Table claim_reprocess_audit: Change remarks and extra_remarks to TEXT
ALTER TABLE claim_reprocess_audit
    ALTER COLUMN remarks TYPE TEXT,
    ALTER COLUMN extra_remarks TYPE TEXT;
