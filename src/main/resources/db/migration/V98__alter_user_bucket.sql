ALTER TABLE public.user_bucket
ADD COLUMN IF NOT EXISTS claim_status varchar(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'claims_status_check'
          AND t.relname = 'user_bucket'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE public.user_bucket
        ADD CONSTRAINT claims_status_check
        CHECK (
            claim_status::bpchar = ANY (
                ARRAY[
                    'IN_REVIEW'::bpchar,
                    'PENDING'::bpchar,
                    'APPROVED'::bpchar,
                    'REJECTED'::bpchar,
                    'CLINICAL_REVIEW'::bpchar,
                    'SUBMITTED'::bpchar,
                    'QUEUED'::bpchar,
                    'SENT_BACK'::bpchar,
                    'UNKNOWN'::bpchar,
                    'UNDER_COMMITTEE_REVIEW'::bpchar,
                    'CLARIFICATION_AFTER_PAYER_CHECKS'::bpchar,
                    'SENT_TO_SURVEILLANCE'::bpchar,
                    'SENT_FOR_PAYMENT_PROCESSING'::bpchar,
                    'PAYMENT_COMPLETED'::bpchar,
                    'PAYMENT_DECLINED'::bpchar,
                    'MEDICAL_REVIEW'::bpchar,
                    'CANCELED'::bpchar,
                    'PAYMENT_PARTIAL'::bpchar,
                    'DECLINE'::bpchar,
                    'MISSING_DOCUMENTS'::bpchar,
                    'RESUBMITTED_MISSING_DOCUMENTS'::bpchar,
                    'MANUAL_REVIEW'::bpchar,
                    'UNDER_SURVEILLANCE'::bpchar
                ]
            )
        );
    END IF;
END $$;
