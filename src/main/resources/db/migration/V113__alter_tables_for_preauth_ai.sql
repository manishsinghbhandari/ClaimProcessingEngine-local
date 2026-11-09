ALTER TABLE provider
ADD COLUMN IF NOT EXISTS preauth_efwa_enabled BOOLEAN DEFAULT FALSE;

ALTER TABLE benefits_master
ADD COLUMN IF NOT EXISTS preauth_efwa_enabled BOOLEAN DEFAULT FALSE;

ALTER TABLE ai_validation_score
ADD COLUMN IF NOT EXISTS "use" VARCHAR(20) DEFAULT 'Claim';

ALTER TABLE ai_validation_mapping
ADD COLUMN IF NOT EXISTS "use" VARCHAR(20) DEFAULT 'Claim';

ALTER TABLE benefit_document_type_mapping
ADD COLUMN IF NOT EXISTS "use" VARCHAR(20) DEFAULT 'Claim';


