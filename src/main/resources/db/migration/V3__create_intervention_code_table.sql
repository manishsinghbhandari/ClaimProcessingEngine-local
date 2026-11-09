CREATE TABLE IF NOT EXISTS public.intervention_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    code_combination TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT NOW() NOT NULL,
    created_by VARCHAR(50) DEFAULT 'SYSTEM' NOT NULL,
    last_modified_date TIMESTAMP DEFAULT NOW() NOT NULL,
    last_modified_by VARCHAR(50) DEFAULT 'SYSTEM' NOT NULL
);

-- Create index on intervention_code column
CREATE INDEX IF NOT EXISTS idx_intervention_code
    ON public.intervention_code (code);