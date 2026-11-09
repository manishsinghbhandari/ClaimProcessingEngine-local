-- Update benefits_master with matching package_master_id
UPDATE benefits_master bm
    SET package_master_id = pm.id
FROM package_master pm
WHERE bm.code = pm.code
  AND bm.package_master_id IS NULL; -- Only update if not already set