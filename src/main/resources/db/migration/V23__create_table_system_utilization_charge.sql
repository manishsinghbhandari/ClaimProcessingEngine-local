CREATE TABLE if not exists public.system_utilization_charge (
    id BIGSERIAL PRIMARY KEY,
    created_by varchar(255) NULL,
    created_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by varchar(255) NULL,
    last_modified_date timestamp(6) NULL,
    effective_date timestamp(6) NOT NULL,
    maximum_value numeric(38, 2) NULL,
    rate numeric(38, 2) NOT NULL,
    status varchar(255) NOT NULL,
    CONSTRAINT system_utilization_charge_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('ACTIVE'::character varying)::text, ('DELETED'::character varying)::text])))
);