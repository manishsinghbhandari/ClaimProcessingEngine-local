-- V2026.02.03.1430__INSERT_benefits_master_SHA_19_Endoscopy.sql
-- ==============================================================
-- Insert Endoscopy benefit into benefits_master table
-- Condition: Only insert if intervention_code 'SHA-19-652' doesn't exist
-- ==============================================================

DO $$
DECLARE
    v_next_id INTEGER;
    v_record_exists BOOLEAN;
    v_intervention_code VARCHAR(20) := 'SHA-19-652';
BEGIN
    -- Check if record already exists with the given intervention_code
    SELECT EXISTS(
        SELECT 1 FROM public.benefits_master 
        WHERE intervention_code = v_intervention_code
        AND status = 'Active'
    ) INTO v_record_exists;
    
    IF NOT v_record_exists THEN
        -- Get the next ID safely
        SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM public.benefits_master;
        
        -- Insert the new record
        INSERT INTO public.benefits_master (
            id, code, sha_category, intervention_code, intervention_name, 
            access_code, gender, sub_category_benefits_package, needs_pre_auth, 
            provider_payment_mechanism, limits_individual_household, limit_individual, 
            pmf_coverage, intervention_quantity_year, applies_to_schemes, min_age, 
            max_age, category_health_facility, tariffs, level_2_tariff, level_3_tariff, 
            level_4_tariff, level_5_tariff, level_6_tariff, phc, shif, eccif, pmsf, 
            automate_manual, complexity, drs_authorizaton, approver_1, approver_2, 
            approver_3, diagnosis_category, diagnosis_sub_category, effective_from, 
            effective_till, status, state, created_date, last_modified_date, 
            created_by, last_modified_by, group_name, is_per_diem_club, bed_type, 
            package_master_id, cycle_limit_in_day, efwa_check_enabled, daily_limit, 
            sub_package
        ) VALUES (
            v_next_id,                    -- id
            'SHA-19',                     -- code
            'Surgical Services',          -- sha_category
            v_intervention_code,          -- intervention_code
            'Endoscopy',                  -- intervention_name
            'IP',                         -- access_code
            'ALL',                        -- gender
            'General surgery',            -- sub_category_benefits_package
            true,                         -- needs_pre_auth
            'FEE FOR SERVICE',            -- provider_payment_mechanism
            'HOUSEHOLD',                  -- limits_individual_household
            NULL,                         -- limit_individual
            'HOUSEHOLD',                  -- pmf_coverage
            NULL,                         -- intervention_quantity_year
            'PMSF',                       -- applies_to_schemes
            NULL,                         -- min_age
            NULL,                         -- max_age
            '4,5,6',                      -- category_health_facility
            '0',                          -- tariffs
            NULL,                         -- level_2_tariff
            NULL,                         -- level_3_tariff
            NULL,                         -- level_4_tariff
            NULL,                         -- level_5_tariff
            NULL,                         -- level_6_tariff
            '0',                          -- phc
            '0',                          -- shif
            '0',                          -- eccif
            '100%',                       -- pmsf
            'Automate',                   -- automate_manual
            'Minor',                      -- complexity
            'YES',                        -- drs_authorizaton
            '',                           -- approver_1
            '',                           -- approver_2
            '',                           -- approver_3
            NULL,                         -- diagnosis_category
            NULL,                         -- diagnosis_sub_category
            NULL,                         -- effective_from
            NULL,                         -- effective_till
            'Active',                     -- status
            '',                           -- state
            CURRENT_TIMESTAMP,            -- created_date
            CURRENT_TIMESTAMP,            -- last_modified_date
            '',                           -- created_by
            '',                           -- last_modified_by
            NULL,                         -- group_name
            false,                        -- is_per_diem_club
            NULL,                         -- bed_type
            NULL,                         -- package_master_id
            NULL,                         -- cycle_limit_in_day
            true,                         -- efwa_check_enabled
            1,                            -- daily_limit
            NULL                         -- sub_package
        );
        
        RAISE NOTICE 'Successfully inserted benefits_master record with id: % and intervention_code: %', 
                     v_next_id, v_intervention_code;
    ELSE
        RAISE NOTICE 'Record with intervention_code % already exists. Skipping insertion.', 
                     v_intervention_code;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Error inserting record: %', SQLERRM;
END $$;