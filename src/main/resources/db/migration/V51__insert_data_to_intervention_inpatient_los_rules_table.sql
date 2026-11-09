INSERT INTO public.intervention_inpatient_los_rules
(package_master_id, "level", ownership, duration, "operator", action_status, created_date, last_modified_date)
VALUES
  (12, 'LEVEL 3', 'Private', 3, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 3', 'Faith-Based', 3, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 3', 'GOK', 3, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 4', 'Private', 4, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 4', 'Faith-Based', 4, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 4', 'GOK', 4, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 5', 'GOK', 8, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 6', 'Faith-Based', 8, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 6', 'Private', 8, '>=', 'MEDICAL_REVIEW', now(), now()),
    (12, 'LEVEL 6', 'GOK', 8, '>=', 'MEDICAL_REVIEW', now(), now());
