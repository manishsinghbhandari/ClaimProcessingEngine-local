CREATE TABLE if not exists public.system_vendor (
    id BIGSERIAL PRIMARY KEY,
    created_by varchar(255) NULL,
    created_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by varchar(255) NULL,
    last_modified_date timestamp(6) NULL,
    bank_account_name varchar(255) NOT NULL,
    bank_account_number varchar(255) NOT NULL,
    bank_swift_code varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    vendor_code varchar(255) NOT NULL,
    vendor_name varchar(255) NOT NULL,
    CONSTRAINT system_vendor_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('ACTIVE'::character varying)::text, ('DELETED'::character varying)::text])))
);