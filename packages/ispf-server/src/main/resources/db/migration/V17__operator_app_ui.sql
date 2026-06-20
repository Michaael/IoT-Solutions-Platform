CREATE TABLE operator_app_ui (
    app_id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    default_dashboard VARCHAR(512),
    dashboards_json CLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
