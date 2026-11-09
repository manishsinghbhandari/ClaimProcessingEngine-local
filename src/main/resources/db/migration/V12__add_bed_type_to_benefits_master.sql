-- Add 'bed_type' column to benefits_master if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'benefits_master'
          AND column_name = 'bed_type'
    ) THEN
        ALTER TABLE benefits_master ADD COLUMN bed_type VARCHAR(50);
        RAISE NOTICE 'Column bed_type added to benefits_master';
    ELSE
        RAISE NOTICE 'Column bed_type already exists';
    END IF;
END
$$;

-- Set initial values for known benefit IDs
UPDATE benefits_master SET bed_type = 'NORMAL'      WHERE intervention_code = 'SHA-07-001';
UPDATE benefits_master SET bed_type = 'ICU'         WHERE intervention_code = 'SHA-07-002';
UPDATE benefits_master SET bed_type = 'HDU'         WHERE intervention_code = 'SHA-07-005';
UPDATE benefits_master SET bed_type = 'DIALYSIS'    WHERE intervention_code = 'SHA-07-006';
