ALTER TABLE object_nodes ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE object_nodes ADD COLUMN IF NOT EXISTS last_changed_by VARCHAR(128);
ALTER TABLE object_nodes ADD COLUMN IF NOT EXISTS last_changed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS object_config_audit (
    id              VARCHAR(64)  PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL,
    change_type     VARCHAR(64)  NOT NULL,
    field           VARCHAR(256),
    actor           VARCHAR(128),
    occurred_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision_before BIGINT,
    revision_after  BIGINT,
    summary_json    TEXT
);

CREATE INDEX IF NOT EXISTS idx_object_config_audit_path
    ON object_config_audit (object_path, occurred_at DESC);

CREATE TABLE IF NOT EXISTS object_edit_leases (
    id           VARCHAR(64)  PRIMARY KEY,
    path_prefix  VARCHAR(512) NOT NULL UNIQUE,
    holder       VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_object_edit_leases_expires
    ON object_edit_leases (expires_at);

CREATE TABLE IF NOT EXISTS platform_change_sets (
    id              VARCHAR(64)  PRIMARY KEY,
    title           VARCHAR(256) NOT NULL,
    author          VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    base_snapshot   TEXT,
    ops_json        TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_change_sets_status
    ON platform_change_sets (status, updated_at DESC);
