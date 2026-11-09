-- Safely add the new column
ALTER TABLE benefits_master ADD COLUMN IF NOT EXISTS combined_codes VARCHAR(500);

-- Update for SHA codes and their corresponding PMF codes
UPDATE benefits_master
SET combined_codes =
    CASE
        -- SHA-07 series and their PMF counterparts
        WHEN intervention_code IN ('SHA-07-001', 'PMF-07-001') THEN 'SHA-07-001,PMF-07-001'
        WHEN intervention_code IN ('SHA-07-002', 'PMF-07-002') THEN 'SHA-07-002,PMF-07-002'
        WHEN intervention_code IN ('SHA-07-003', 'PMF-07-003') THEN 'SHA-07-003,PMF-07-003'
        WHEN intervention_code IN ('SHA-07-004', 'PMF-07-004') THEN 'SHA-07-004,PMF-07-004'

        -- SHA-03 series and their PMF counterparts
        WHEN intervention_code IN ('SHA-03-001', 'PMF-03-001') THEN 'SHA-03-001,PMF-03-001'
        WHEN intervention_code IN ('SHA-03-002', 'PMF-03-002') THEN 'SHA-03-002,PMF-03-002'
        WHEN intervention_code IN ('SHA-03-003', 'PMF-03-003') THEN 'SHA-03-003,PMF-03-003'
        WHEN intervention_code IN ('SHA-03-004', 'PMF-03-004') THEN 'SHA-03-004,PMF-03-004'
        WHEN intervention_code IN ('SHA-03-005', 'PMF-03-005') THEN 'SHA-03-005,PMF-03-005'

        -- SHA-10 series and their PMF counterparts
        WHEN intervention_code IN ('SHA-10-002', 'PMF-10-002') THEN 'SHA-10-002,PMF-10-002'
        WHEN intervention_code IN ('SHA-10-003', 'PMF-10-003') THEN 'SHA-10-003,PMF-10-003'
        WHEN intervention_code IN ('SHA-10-004', 'PMF-10-004') THEN 'SHA-10-004,PMF-10-004'
        WHEN intervention_code IN ('SHA-10-005', 'PMF-10-005') THEN 'SHA-10-005,PMF-10-005'

        -- SHA-13 series
        WHEN intervention_code IN ('SHA-13-001', 'PMF-13-001') THEN 'SHA-13-001,PMF-13-001'
    END
WHERE intervention_code IN (
    -- SHA codes
    'SHA-07-001', 'SHA-07-002', 'SHA-07-003', 'SHA-07-004',
    'SHA-03-001', 'SHA-03-002', 'SHA-03-003', 'SHA-03-004', 'SHA-03-005',
    'SHA-10-002', 'SHA-10-003', 'SHA-10-004', 'SHA-10-005',
    'SHA-13-001',
    -- Corresponding PMF codes
    'PMF-07-001', 'PMF-07-002', 'PMF-07-003', 'PMF-07-004',
    'PMF-03-001', 'PMF-03-002', 'PMF-03-003', 'PMF-03-004', 'PMF-03-005',
    'PMF-10-002', 'PMF-10-003', 'PMF-10-004', 'PMF-10-005',
    'PMF-13-001'
);

UPDATE benefits_master
SET applies_to_schemes = 'SHA,PMSF'
WHERE intervention_code IN (
    -- SHA codes
    'SHA-07-001', 'SHA-07-002', 'SHA-07-003', 'SHA-07-004',
    'SHA-03-001', 'SHA-03-002', 'SHA-03-003', 'SHA-03-004', 'SHA-03-005',
    'SHA-10-002', 'SHA-10-003', 'SHA-10-004', 'SHA-10-005',
    'SHA-13-001',
    -- Corresponding PMF codes
    'PMF-07-001', 'PMF-07-002', 'PMF-07-003', 'PMF-07-004',
    'PMF-03-001', 'PMF-03-002', 'PMF-03-003', 'PMF-03-004', 'PMF-03-005',
    'PMF-10-002', 'PMF-10-003', 'PMF-10-004', 'PMF-10-005',
    'PMF-13-001'
)
AND combined_codes IS NULL;