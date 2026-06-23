-- Snapshot operator app on work queue tasks (from workflow.operatorAppId at task creation)

ALTER TABLE workflow_user_tasks
    ADD COLUMN IF NOT EXISTS operator_app_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_workflow_user_tasks_operator_app
    ON workflow_user_tasks(operator_app_id, status, created_at DESC);
