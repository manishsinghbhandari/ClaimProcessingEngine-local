-- create rejection_category
CREATE TABLE IF NOT EXISTS rejection_category (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  category_name VARCHAR(100) NOT NULL,
    prefix VARCHAR(10) NOT NULL,

    last_sequence VARCHAR(3) NOT NULL DEFAULT '000',

    CONSTRAINT uq_rejection_category_name UNIQUE (category_name),
    CONSTRAINT uq_rejection_category_prefix UNIQUE (prefix),
    CONSTRAINT chk_last_sequence_format
    CHECK (last_sequence ~ '^[0-9]{3}$')
    );

-- create rejection_code
CREATE TABLE IF NOT EXISTS rejection_code (
                                              id BIGSERIAL PRIMARY KEY,
                                              category_id BIGINT NOT NULL,
                                              code VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_by VARCHAR(255) NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255) NOT NULL,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rejection_code_category
    FOREIGN KEY (category_id)
    REFERENCES rejection_category(id)
    ON DELETE RESTRICT,

    CONSTRAINT uq_rejection_code UNIQUE (code)
    );