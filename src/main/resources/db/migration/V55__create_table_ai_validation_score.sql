CREATE TABLE if not exists public.ai_validation_score (
    id BIGSERIAL PRIMARY KEY,
    decision varchar(255) NOT NULL,
    max_score float8 NOT NULL,
    message varchar(255) NOT NULL,
    min_score float8 NOT NULL,
    "name" varchar(255) NOT NULL,
    validation_key varchar(255) NOT NULL,
    CONSTRAINT ai_validation_score_decision_check CHECK (((decision)::text = ANY ((ARRAY['REJECT'::character varying, 'MANUAL_REVIEW'::character varying, 'APPROVE'::character varying])::text[])))
);