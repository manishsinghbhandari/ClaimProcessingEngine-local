-- Create table phc_batch_payments with FK to admin_level_populations
CREATE TABLE IF NOT EXISTS public.phc_batch_payments (
    id BIGSERIAL PRIMARY KEY,
    admin_level_pop_id BIGINT NULL,
    date DATE NOT NULL,
    amount NUMERIC(38,2) NOT NULL,
    approved_amount NUMERIC(38,2),
    paid_amount NUMERIC(38,2),
    number_of_claims BIGINT NULL,
    global_budget NUMERIC(38,2),
    status VARCHAR(50) NOT NULL,
    remarks TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT phc_batch_payments_status_check CHECK ((status)::text = ANY (ARRAY[
        'PENDING'::text,
        'APPROVED'::text,
        'SENT_FOR_PAYMENT_PROCESSING'::text,
        'PAYMENT_COMPLETED'::text,
        'PAYMENT_PARTIAL'::text,
        'PARTIAL_APPROVAL'::text,
        'REJECTED'::text,
        'FAILED'::text
    ])),
    CONSTRAINT fk_phc_batch_admin_pop FOREIGN KEY (admin_level_pop_id) REFERENCES public.admin_level_populations(id) ON DELETE SET NULL
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_batch_status ON public.phc_batch_payments USING btree (status);
CREATE INDEX IF NOT EXISTS idx_batch_admin_pop_id ON public.phc_batch_payments USING btree (admin_level_pop_id);