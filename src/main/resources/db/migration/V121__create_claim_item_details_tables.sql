CREATE TABLE IF NOT EXISTS claim_item_details (
    id       BIGSERIAL       PRIMARY KEY,
    item_id         BIGINT          NOT NULL,
    sequence        INTEGER,
    code            VARCHAR(50),
    code_display    VARCHAR(255),
    code_system     VARCHAR(255),
    category        VARCHAR(100),
    quantity        BIGINT,
    unit_price      NUMERIC(19, 2),
    factor          NUMERIC(10, 4),
    net_value       NUMERIC(19, 2),
    currency        VARCHAR(10)     DEFAULT 'KES',
    created_by      VARCHAR(255),
    created_date    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by    VARCHAR(255),
    last_modified_date  TIMESTAMP,
    CONSTRAINT fk_claim_item_details_item
        FOREIGN KEY (item_id) REFERENCES claim_items (item_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_item_details_item_id
    ON claim_item_details (item_id);