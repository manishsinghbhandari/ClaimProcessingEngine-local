BEGIN;

-- Step 1: Delete from ai_validation_mapping
DELETE FROM ai_validation_mapping
WHERE benefit_master_id IN (
    SELECT id FROM benefits_master
    WHERE id NOT IN (
        SELECT MAX(id) FROM benefits_master GROUP BY intervention_code
    )
);

-- Step 2: Delete from benefit_document_type_mapping
DELETE FROM benefit_document_type_mapping
WHERE benefit_master_id IN (
    SELECT id FROM benefits_master
    WHERE id NOT IN (
        SELECT MAX(id) FROM benefits_master GROUP BY intervention_code
    )
);

-- Step 3: Delete from benefits_master_log
DELETE FROM benefits_master_log
WHERE benefit_master_id IN (
    SELECT id FROM benefits_master
    WHERE id NOT IN (
        SELECT MAX(id) FROM benefits_master GROUP BY intervention_code
    )
);

-- Step 4: Delete from patient_utilization
DELETE FROM patient_utilization
WHERE benefits_master_id IN (
    SELECT id FROM benefits_master
    WHERE id NOT IN (
        SELECT MAX(id) FROM benefits_master GROUP BY intervention_code
    )
);

-- Step 5: Finally, delete the duplicates from benefits_master
DELETE FROM benefits_master
WHERE id NOT IN (
    SELECT MAX(id) FROM benefits_master GROUP BY intervention_code
);

COMMIT;
