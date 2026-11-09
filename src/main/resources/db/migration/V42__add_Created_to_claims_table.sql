-- 1. Add the new column (ignore if it already exists)
ALTER TABLE claims ADD COLUMN IF NOT EXISTS claim_created_at DATE;