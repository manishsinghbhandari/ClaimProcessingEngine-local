-- Benefits Master Log Table
CREATE TABLE IF NOT EXISTS public.benefits_master_log (
    id BIGSERIAL PRIMARY KEY,
    benefit_master_id BIGINT NOT NULL,
    previous_record JSONB NOT NULL CHECK (jsonb_typeof(previous_record) = 'object'),
    current_record JSONB NOT NULL CHECK (jsonb_typeof(current_record) = 'object'),
    created_by VARCHAR(255) NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMPTZ,
    CONSTRAINT fk_benefits_master
        FOREIGN KEY (benefit_master_id)
        REFERENCES public.benefits_master(id)
        --ON DELETE CASCADE // Need to know the relation so that child record automatically won't allow
        -- to delete
);

-- Indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_benefits_master_log_benefit_master_id
    ON public.benefits_master_log(benefit_master_id);

CREATE INDEX IF NOT EXISTS idx_benefits_master_log_created_date
    ON public.benefits_master_log(created_date DESC);

