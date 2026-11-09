-- Create ICD codes table to replace MongoDB collection
CREATE TABLE IF NOT EXISTS public.icd_codes (
    id BIGSERIAL PRIMARY KEY,
    icd_10_disease_code VARCHAR(255) NOT NULL,
    icd_11_codes VARCHAR(255),
    main_code VARCHAR(255),
    sub_code VARCHAR(255),
    weight DECIMAL(10,2) NOT NULL DEFAULT 0.0,
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,
    
    -- Indexes for performance
    CONSTRAINT icd_codes_icd10_unique UNIQUE (icd_10_disease_code)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_icd_codes_icd11 ON public.icd_codes (icd_11_codes);
CREATE INDEX IF NOT EXISTS idx_icd_codes_main_code ON public.icd_codes (main_code);
CREATE INDEX IF NOT EXISTS idx_icd_codes_sub_code ON public.icd_codes (sub_code);
CREATE INDEX IF NOT EXISTS idx_icd_codes_weight ON public.icd_codes (weight);