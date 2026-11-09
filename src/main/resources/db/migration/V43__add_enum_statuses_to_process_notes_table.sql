-- 1. Drop the existing constraint
ALTER TABLE public.process_notes
    DROP CONSTRAINT process_notes_status_check;

-- 2. Add the updated constraint
ALTER TABLE public.process_notes
    ADD CONSTRAINT process_notes_status_check
    CHECK (
        (status)::bpchar = ANY (
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
                'RESUBMITTED_MISSING_DOCUMENTS'::bpchar
            ]
        )
    );