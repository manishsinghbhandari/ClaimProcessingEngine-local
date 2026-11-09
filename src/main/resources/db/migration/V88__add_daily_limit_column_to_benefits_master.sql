ALTER TABLE benefits_master
  ADD COLUMN IF NOT EXISTS daily_limit integer NOT NULL DEFAULT 1;