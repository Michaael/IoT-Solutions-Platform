ALTER TABLE object_nodes
    ADD COLUMN IF NOT EXISTS function_audit_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_object_nodes_function_audit_enabled
    ON object_nodes (function_audit_enabled);
