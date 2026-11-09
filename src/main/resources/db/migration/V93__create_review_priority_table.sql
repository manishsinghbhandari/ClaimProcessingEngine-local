CREATE TABLE IF NOT EXISTS public.review_priority (
	id BIGSERIAL PRIMARY KEY,
	created_by varchar(255) NULL,
	created_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	last_modified_by varchar(255) NULL,
	last_modified_date timestamp(6) NULL,
	category varchar(255) NOT NULL,
	priority int4 NOT NULL,
	status varchar(255) NOT NULL,
	category_value varchar(255) NOT NULL,
	value varchar(255) NOT NULL,
	CONSTRAINT review_priority_category_check CHECK (((category)::text = ANY ((ARRAY['PACKAGE_SUB_PACKAGE'::character varying, 'FACILITY'::character varying, 'OWNERSHIP'::character varying, 'LEVEL'::character varying])::text[]))),
	CONSTRAINT review_priority_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DELETED'::character varying])::text[])))
);