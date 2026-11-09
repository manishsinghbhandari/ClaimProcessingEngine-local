ALTER TABLE public.review_priority
ADD COLUMN IF NOT EXISTS name varchar(255) NOT NULL;