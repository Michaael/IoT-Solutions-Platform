ALTER TABLE object_nodes
    ADD COLUMN IF NOT EXISTS binding_audit_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_object_nodes_binding_audit_enabled
    ON object_nodes (binding_audit_enabled);
