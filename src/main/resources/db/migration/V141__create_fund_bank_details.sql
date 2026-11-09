CREATE TABLE IF NOT EXISTS fund_bank_details (
    id BIGSERIAL PRIMARY KEY,
    fund_scheme VARCHAR(50) NOT NULL,
    fund_bank_name VARCHAR(255) NOT NULL,
    fund_branch_code VARCHAR(100) NOT NULL,
    fund_bank_account_number VARCHAR(255) NOT NULL,
    erp_bank_name VARCHAR(255) NOT NULL,
    created_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100)
);
