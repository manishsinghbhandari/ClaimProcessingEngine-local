INSERT INTO public.intervention_code
(code, description, code_combination, created_date, created_by, last_modified_date, last_modified_by)
VALUES
    ('SHA-01', 'Ambulance and Emergency Services', '["SHA-01-003", "SHA-01-004", "SHA-01-005", "SHA-01-006", "SHA-01-007", "SHA-01-008","SHA-01-009" , "SHA-01-013"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-03', 'Critical Care Services', '["SHA-07","SHA-06","SHA-16","SHA-09","SHA-13","SHA-08","SHA-19"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-05', 'Optical Health Services', '["SHA-05"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-06', 'Haematology and Oncology Services', '["SHA-06"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-07', 'Inpatient Services', '["SHA-03", "SHA-06", "SHA-16-001", "SHA-16-002", "SHA-16-004", "SHA-16-007", "SHA-16-008", "SHA-16-011", "SHA-09", "SHA-19"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-08', 'Maternity and Child Health Services', '["SHA-03", "SHA-09", "SHA-07-005", "SHA-07-006"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-09', 'Medical Imaging & Other Investigations', '["SHA-09"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-10', 'Mental Wellness Services', '["SHA-10"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-12', 'Outpatient Services', '["SHA-12"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-13', 'Palliative Care services', '["SHA-03", "SHA-06", "SHA-07", "SHA-09", "SHA-16", "SHA-19"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-001', 'Renal Care Services', '["SHA-16-002", "SHA-16-004"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-002', 'Renal Care Services', '["SHA-16-001", "SHA-16-004"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-003', 'Renal Care Services', '["SHA-03", "SHA-07"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-004', 'Renal Care Services', '["SHA-16-001", "SHA-16-002"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-005', 'Renal Care Services', '["SHA-03", "SHA-07"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-006', 'Renal Care Services', '["SHA-03", "SHA-07"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-007', 'Renal Care Services', '["SHA-03", "SHA-07"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-16-009', 'Renal Care Services', '["SHA-03", "SHA-07"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-18', 'Essential Diagnostic Laboratory Listing for NCDs', '["SHA-18"]', now(), 'Prashant', now(), 'Prashant'),
    ('SHA-19', 'Surgical Services', '["SHA-07-002", "SHA-09", "SHA-03", "SHA-13"]', now(), 'Prashant', now(), 'Prashant');
