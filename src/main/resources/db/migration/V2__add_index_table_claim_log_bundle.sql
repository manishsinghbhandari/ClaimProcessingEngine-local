CREATE INDEX IF NOT EXISTS idx_claim_bundle_log_claim_id
  ON public.claim_bundle_log (claim_id);
