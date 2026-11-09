-- Create admin_level_population table for AdminLevelPopulation
CREATE TABLE IF NOT EXISTS public.admin_level_populations (
    id BIGSERIAL PRIMARY KEY,
    admin_level_id BIGINT NOT NULL DEFAULT 1,
    population BIGINT NOT NULL,
    month VARCHAR(20) NOT NULL,
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,

    CONSTRAINT adl_population_unique_adl_id_month UNIQUE (admin_level_id, month),
    CONSTRAINT adl_population_adl_fk FOREIGN KEY (admin_level_id) REFERENCES public.admin_level_ones(id) ON DELETE CASCADE
);

