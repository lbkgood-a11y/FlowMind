-- Action correlation metadata for workflow runtime records.

ALTER TABLE wf_process_instance
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wf_process_instance_action
    ON wf_process_instance(tenant_id, action_id)
    WHERE action_id IS NOT NULL;

ALTER TABLE wf_task_operation
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wf_task_operation_action
    ON wf_task_operation(action_id)
    WHERE action_id IS NOT NULL;

ALTER TABLE wf_process_outcome
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wf_process_outcome_action
    ON wf_process_outcome(tenant_id, action_id)
    WHERE action_id IS NOT NULL;

ALTER TABLE wf_process_closure
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wf_process_closure_action
    ON wf_process_closure(action_id)
    WHERE action_id IS NOT NULL;

ALTER TABLE wf_closure_effect
    ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS action_actor_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_actor_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS action_correlation_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_wf_closure_effect_action
    ON wf_closure_effect(action_id)
    WHERE action_id IS NOT NULL;
