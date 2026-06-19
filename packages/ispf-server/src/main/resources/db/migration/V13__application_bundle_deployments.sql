CREATE TABLE application_bundle_deployments (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    bundle_version VARCHAR(64) NOT NULL,
    manifest_json TEXT NOT NULL,
    operator_manifest_json TEXT,
    deployed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_app_bundle_version UNIQUE (app_id, bundle_version)
);

CREATE INDEX idx_app_bundle_active ON application_bundle_deployments (app_id, is_active);
