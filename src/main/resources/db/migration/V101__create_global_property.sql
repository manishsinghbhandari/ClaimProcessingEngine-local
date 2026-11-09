CREATE TABLE IF NOT EXISTS public.global_property (
    id BIGSERIAL PRIMARY KEY,
    created_by varchar(255) NULL,
    created_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by varchar(255) NULL,
    last_modified_date timestamp(6) NULL,
    deleted bool NOT NULL,
    property varchar(255) NOT NULL,
    status bool NOT NULL,
    value varchar(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_global_property_key_deleted_false
ON public.global_property (property)
WHERE deleted = false;
