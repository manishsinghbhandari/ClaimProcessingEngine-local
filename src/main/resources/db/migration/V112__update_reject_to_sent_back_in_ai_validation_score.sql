-- This migration updates existing decision values from 'REJECT' to 'SENT_BACK' to align with the renamed Decision enum value
UPDATE public.ai_validation_score 
SET decision = 'SENT_BACK', message = 'Sent Back due to Bad Image Quality'
WHERE decision = 'REJECT';
