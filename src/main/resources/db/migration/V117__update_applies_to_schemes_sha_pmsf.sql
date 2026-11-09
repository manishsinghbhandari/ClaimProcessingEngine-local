-- Update applies_to_schemes for SHA and PMF intervention codes
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
);
