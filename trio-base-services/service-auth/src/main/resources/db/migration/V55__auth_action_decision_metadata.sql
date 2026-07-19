ALTER TABLE sys_auth_decision_log
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_target_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_target_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_payload_metadata TEXT;

CREATE INDEX IF NOT EXISTS idx_sys_auth_decision_log_action
    ON sys_auth_decision_log(tenant_id, action_type, decided_at DESC)
    WHERE action_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sys_auth_decision_log_action_id
    ON sys_auth_decision_log(action_id)
    WHERE action_id IS NOT NULL;
