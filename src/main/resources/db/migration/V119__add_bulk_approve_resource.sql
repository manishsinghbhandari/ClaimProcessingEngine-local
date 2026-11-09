-- First, drop the existing CHECK constraint that restricts the name column to known ResourceEnum values
ALTER TABLE resources DROP CONSTRAINT IF EXISTS resources_name_check;

-- Re-add the constraint with CLAIM_BULK_APPROVE included
ALTER TABLE resources ADD CONSTRAINT resources_name_check CHECK (
    name::text = ANY (ARRAY[
        'ADMIN', 'PREAUTHORIZATIONS', 'CLAIMS', 'PHC_CLAIMS', 'USER_MANAGE',
        'INTERVENTIONS', 'ASSIGNMENTS', 'CODES', 'ORGANIZATION', 'PATIENT',
        'PRACTITIONER', 'UNKNOWN', 'GLOBAL_PROPERTY', 'CLAIM_BULK_APPROVE'
    ]::text[])
);

-- Add CLAIM_BULK_APPROVE resource
INSERT INTO resources (name, created_date, last_modified_date, created_by, last_modified_by)
VALUES ('CLAIM_BULK_APPROVE', NOW(), NOW(), 'admin', 'admin')
ON CONFLICT (name) DO NOTHING;
