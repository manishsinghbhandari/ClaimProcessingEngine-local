-- Create provider_attributes table if not exists
CREATE TABLE IF NOT EXISTS provider_attributes (
    id BIGSERIAL PRIMARY KEY,

    provider_id BIGINT NOT NULL,
    is_vems_supported BOOLEAN NOT NULL DEFAULT FALSE,

    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    -- Auditing columns from AuditableBase
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,

    -- Foreign key reference to provider(id)
    CONSTRAINT fk_provider_attributes_provider
        FOREIGN KEY (provider_id)
        REFERENCES provider(id)
);
