ALTER TABLE applications ADD COLUMN IF NOT EXISTS schema_name VARCHAR(64);

CREATE TABLE IF NOT EXISTS application_data_seeds (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    profile VARCHAR(64) NOT NULL,
    seed_id VARCHAR(128) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_seed UNIQUE (app_id, profile, seed_id)
);
