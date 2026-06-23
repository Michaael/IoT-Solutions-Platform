ALTER TABLE object_nodes ADD COLUMN IF NOT EXISTS applied_model_ids TEXT;

UPDATE object_nodes
SET applied_model_ids = '["' || REPLACE(template_id, '"', '\"') || '"]'
WHERE template_id IS NOT NULL
  AND template_id <> ''
  AND (applied_model_ids IS NULL OR TRIM(applied_model_ids) = '');
