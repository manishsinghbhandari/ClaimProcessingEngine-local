CREATE TABLE IF NOT EXISTS public.patient_utilization (
    id uuid PRIMARY KEY NULL,
    household_utilised_limit int4 NOT NULL,
    individual_utilised_limit int4 NOT NULL,
    next_availability date NOT NULL,
    benefits_master_id int8 NOT NULL,
    patient_id uuid NOT NULL,
    CONSTRAINT patient_utilization_patient_fk FOREIGN KEY (patient_id) REFERENCES public.patient(id),
    CONSTRAINT patient_utilization_benefits_master_fk FOREIGN KEY (benefits_master_id) REFERENCES public.benefits_master(id)
);

CREATE TABLE IF NOT EXISTS public.fund_utilization_limit (
    id uuid PRIMARY KEY NULL,
    fund_type varchar(50) NOT NULL,
    utilised_amount numeric(19, 2) NOT NULL,
    utilization uuid NOT NULL,
    CONSTRAINT fund_utilization_limit_fund_type_check CHECK (((fund_type)::text = ANY ((ARRAY['ECCIF'::character varying, 'SHIF'::character varying, 'SHA'::character varying, 'PMSF'::character varying])::text[]))),
    CONSTRAINT fund_utilization_limit_patient_id_fk FOREIGN KEY (utilization) REFERENCES public.patient_utilization(id)
);
