CREATE TABLE binding_invoke_audit (
    id UUID PRIMARY KEY,
    binding_kind VARCHAR(16) NOT NULL,
    object_path VARCHAR(512) NOT NULL,
    rule_id VARCHAR(128),
    rule_name VARCHAR(255),
    trigger_kind VARCHAR(32) NOT NULL,
    target_variable VARCHAR(128),
    success BOOLEAN NOT NULL,
    changed BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(1024),
    duration_ms INTEGER,
    invoked_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_binding_invoke_audit_object_path ON binding_invoke_audit (object_path, invoked_at DESC);
CREATE INDEX idx_binding_invoke_audit_invoked_at ON binding_invoke_audit (invoked_at DESC);
