ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS mnemonic varchar(255);

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS addressesJson text;
