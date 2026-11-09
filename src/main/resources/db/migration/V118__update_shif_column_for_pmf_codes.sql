-- Update SHIF column to 100% for benefits_master entries where code starts with 'PMF'
UPDATE benefits_master
SET shif = '100%'
WHERE code LIKE 'PMF%';
