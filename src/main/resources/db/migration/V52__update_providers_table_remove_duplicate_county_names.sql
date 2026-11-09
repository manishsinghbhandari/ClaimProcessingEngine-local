CREATE INDEX IF NOT EXISTS idx_provider_county_name ON provider (county);

UPDATE provider
SET county = CASE
    WHEN county = 'MURANGA' THEN 'MURANG''A'
    WHEN county = 'ELGEYO/ MARAKWET' THEN 'ELGEYO MARAKWET'
    WHEN county = 'Nakuru' THEN 'NAKURU'
ELSE county
END
WHERE county IN ('MURANGA', 'ELGEYO/ MARAKWET', 'Nakuru');