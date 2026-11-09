-- Create admin_level_ones table and seed initial data
CREATE TABLE IF NOT EXISTS public.admin_level_ones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE
);

-- seed level ones if not exists
INSERT INTO public.admin_level_ones (id, name, code) VALUES (1, 'MOMBASA', '001') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (2, 'KWALE', '002') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (3, 'KILIFI', '003') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (4, 'TANA RIVER', '004') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (5, 'LAMU', '005') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (6, 'TAITA TAVETA', '006') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (7, 'GARISSA', '007') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (8, 'WAJIR', '008') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (9, 'MANDERA', '009') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (10, 'MARSABIT', '010') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (11, 'ISIOLO', '011') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (12, 'MERU', '012') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (13, 'THARAKA-NITHI', '013') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (14, 'EMBU', '014') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (15, 'KITUI', '015') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (16, 'MACHAKOS', '016') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (17, 'MAKUENI', '017') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (18, 'NYANDARUA', '018') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (19, 'NYERI', '019') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (20, 'KIRINYAGA', '020') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (21, 'MURANGA', '021') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (22, 'KIAMBU', '022') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (23, 'TURKANA', '023') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (24, 'WEST POKOT', '024') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (25, 'SAMBURU', '025') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (26, 'TRANSZOIA', '026') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (27, 'UASIN GISHU', '027') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (28, 'ELGEYO MARAKWET', '028') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (29, 'NANDI', '029') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (30, 'BARINGO', '030') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (31, 'LAIKIPIA', '031') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (32, 'NAKURU', '032') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (33, 'NAROK', '033') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (34, 'KAJIADO', '034') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (35, 'KERICHO', '035') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (36, 'BOMET', '036') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (37, 'KAKAMEGA', '037') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (38, 'VIHIGA', '038') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (39, 'BUNGOMA', '039') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (40, 'BUSIA', '040') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (41, 'SIAYA', '041') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (42, 'KISUMU', '042') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (43, 'HOMABAY', '043') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (44, 'MIGORI', '044') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (45, 'KISII', '045') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (46, 'NYAMIRA', '046') ON CONFLICT DO NOTHING;
INSERT INTO public.admin_level_ones (id, name, code) VALUES (47, 'NAIROBI', '047') ON CONFLICT DO NOTHING;

-- Align sequence with current max(id) in case of explicit inserts above
SELECT setval(
    pg_get_serial_sequence('public.admin_level_ones', 'id'),
    COALESCE((SELECT MAX(id) FROM public.admin_level_ones), 0),
    true
);
