INSERT INTO package_master (code, sha_category)
SELECT bm.code, MIN(bm.sha_category)
FROM benefits_master bm
WHERE NOT EXISTS (
    SELECT 1 FROM package_master pm WHERE pm.code = bm.code
)
GROUP BY bm.code;