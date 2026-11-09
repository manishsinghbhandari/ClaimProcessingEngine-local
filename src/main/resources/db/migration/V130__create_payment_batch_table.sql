CREATE TABLE IF NOT EXISTS payment_batch (
    batch_id VARCHAR(64) PRIMARY KEY,

    invoice_number VARCHAR(64) NOT NULL,
    purchase_order_number VARCHAR(64) NOT NULL,

    provider_id VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    payment_completion_time TIMESTAMP,
    claim_ids JSONB, -- Supporting List<String> via JSONB

    batch_status VARCHAR(30) NOT NULL,

    payment_gateway_reference VARCHAR(128),
    bank_reference_number VARCHAR(128),

    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_batch_provider
    ON payment_batch (provider_id);

ALTER TABLE public.payment_processing ALTER COLUMN bank_reference_number TYPE varchar(500) USING bank_reference_number::varchar(500);
ALTER TABLE public.payment_processing ALTER COLUMN debit_bank_account TYPE varchar(500) USING debit_bank_account::varchar(500);
ALTER TABLE public.payment_processing ALTER COLUMN debit_bank_name TYPE varchar(500) USING debit_bank_name::varchar(500);
ALTER TABLE public.payment_processing ALTER COLUMN provider_name TYPE varchar(500) USING provider_name::varchar(500);



