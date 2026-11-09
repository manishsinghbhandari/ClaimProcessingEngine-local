-- Add provider_amount and utilization_amount columns to erp_claims table
ALTER TABLE public.erp_claims
    ADD COLUMN IF NOT EXISTS provider_amount NUMERIC(38, 2),
    ADD COLUMN IF NOT EXISTS utilization_amount NUMERIC(38, 2);

-- Add comment for documentation
COMMENT ON COLUMN public.erp_claims.provider_amount IS 'Net amount payable to the provider (claim_amount - utilization_amount)';
COMMENT ON COLUMN public.erp_claims.utilization_amount IS '2% system utilization charge capped at KES 5,000';
