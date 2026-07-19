-- Action correlation metadata for lowcode form instances and workflow audit records.

ALTER TABLE lc_form_instance
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_action
    ON lc_form_instance(tenant_id, action_id)
    WHERE action_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_action_trace
    ON lc_form_instance(tenant_id, action_trace_id, submitted_at DESC)
    WHERE action_trace_id IS NOT NULL;

ALTER TABLE lc_form_instance_workflow_audit
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_workflow_audit_action
    ON lc_form_instance_workflow_audit(tenant_id, action_id, created_at DESC)
    WHERE action_id IS NOT NULL;
