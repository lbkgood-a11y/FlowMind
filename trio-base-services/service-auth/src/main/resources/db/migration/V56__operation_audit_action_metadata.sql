ALTER TABLE sys_operation_audit_log
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_target_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_target_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_idempotency_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS action_summary TEXT;

CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_action
    ON sys_operation_audit_log(tenant_id, action_type, operated_at DESC)
    WHERE action_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_action_id
    ON sys_operation_audit_log(action_id)
    WHERE action_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_action_target
    ON sys_operation_audit_log(tenant_id, action_target_type, action_target_id, operated_at DESC)
    WHERE action_target_type IS NOT NULL;
