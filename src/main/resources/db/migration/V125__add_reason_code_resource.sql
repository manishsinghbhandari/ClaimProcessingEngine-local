-- First, drop the existing CHECK constraint that restricts the name column to known ResourceEnum values
ALTER TABLE resources DROP CONSTRAINT IF EXISTS resources_name_check;

-- Re-add the constraint with REASON_MODULE included
ALTER TABLE resources ADD CONSTRAINT resources_name_check CHECK (
    name::text = ANY (ARRAY[
        'ADMIN', 'PREAUTHORIZATIONS', 'CLAIMS', 'PHC_CLAIMS', 'USER_MANAGE',
        'INTERVENTIONS', 'ASSIGNMENTS', 'CODES', 'ORGANIZATION', 'PATIENT',
        'PRACTITIONER', 'UNKNOWN', 'GLOBAL_PROPERTY', 'CLAIM_BULK_APPROVE','REASON_MODULE'
    ]::text[])
);

-- Add REASON_MODULE resource
INSERT INTO resources (name, created_date, last_modified_date, created_by, last_modified_by)
VALUES ('REASON_MODULE', NOW(), NOW(), 'admin', 'admin')
ON CONFLICT (name) DO NOTHING;
