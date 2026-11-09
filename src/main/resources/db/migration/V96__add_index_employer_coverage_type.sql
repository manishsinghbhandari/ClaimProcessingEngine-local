-- Add indexes to employer and coverage_type columns for improved query performance
CREATE INDEX IF NOT EXISTS idx_claim_attributes_employer ON claim_attributes (employer);
CREATE INDEX IF NOT EXISTS idx_claim_attributes_coverage_type ON claim_attributes (coverage_type);
