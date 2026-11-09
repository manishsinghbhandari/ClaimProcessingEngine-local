-- Drop table if it exists

-- Create table
CREATE TABLE IF NOT EXISTS public.facility_bed_capacity (
	id bigserial NOT NULL,
    provider_id BIGINT NOT NULL,
	total_beds int4 NOT NULL,
	normal_beds int4 NOT NULL,
	icu_beds int4 NOT NULL,
	hdu_beds int4 NOT NULL,
	dialysis_beds int4 NOT NULL,
	number_of_cots int4 NOT NULL,
	created_by varchar(255) NULL,
	created_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	last_modified_by varchar(255) NULL,
	last_modified_date timestamp NULL,
	CONSTRAINT facility_bed_capacity_pkey PRIMARY KEY (id)

);

CREATE INDEX IF NOT EXISTS idx_facility_provider_id ON public.facility_bed_capacity (provider_id);

