ALTER TABLE function_invoke_audit ADD COLUMN IF NOT EXISTS input_json TEXT;
ALTER TABLE function_invoke_audit ADD COLUMN IF NOT EXISTS output_json TEXT;

ALTER TABLE binding_invoke_audit ADD COLUMN IF NOT EXISTS detail_json TEXT;
