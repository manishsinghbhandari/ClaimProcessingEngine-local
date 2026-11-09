-- ======================
-- Create mapping table between benefits_master and document_types
-- ======================
CREATE TABLE IF NOT EXISTS benefit_document_type_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    benefit_master_id BIGINT NOT NULL,
    document_type_id BIGINT NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_benefit_master FOREIGN KEY (benefit_master_id)
        REFERENCES benefits_master (id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_type FOREIGN KEY (document_type_id)
        REFERENCES document_types (id) ON DELETE RESTRICT,
    CONSTRAINT uq_benefit_doc UNIQUE (benefit_master_id, document_type_id)
);

COMMENT ON TABLE benefit_document_type_mapping IS
    'Mapping table linking each benefit to multiple document types (many-to-many)';