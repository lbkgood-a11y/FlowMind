ALTER TABLE oa_execution
    ADD COLUMN action_id VARCHAR(64),
    ADD COLUMN action_type VARCHAR(128),
    ADD COLUMN action_source VARCHAR(32),
    ADD COLUMN action_actor_type VARCHAR(32),
    ADD COLUMN action_actor_id VARCHAR(128),
    ADD COLUMN action_actor_name VARCHAR(128),
    ADD COLUMN action_trace_id VARCHAR(128),
    ADD COLUMN action_correlation_id VARCHAR(128);

ALTER TABLE oa_execution_step_attempt
    ADD COLUMN action_id VARCHAR(64),
    ADD COLUMN action_type VARCHAR(128),
    ADD COLUMN action_source VARCHAR(32),
    ADD COLUMN action_actor_type VARCHAR(32),
    ADD COLUMN action_actor_id VARCHAR(128),
    ADD COLUMN action_actor_name VARCHAR(128),
    ADD COLUMN action_trace_id VARCHAR(128),
    ADD COLUMN action_correlation_id VARCHAR(128);

ALTER TABLE oa_callback_inbox
    ADD COLUMN action_id VARCHAR(64),
    ADD COLUMN action_type VARCHAR(128),
    ADD COLUMN action_source VARCHAR(32),
    ADD COLUMN action_actor_type VARCHAR(32),
    ADD COLUMN action_actor_id VARCHAR(128),
    ADD COLUMN action_actor_name VARCHAR(128),
    ADD COLUMN action_trace_id VARCHAR(128),
    ADD COLUMN action_correlation_id VARCHAR(128);

ALTER TABLE oa_audit_event
    ADD COLUMN action_id VARCHAR(64),
    ADD COLUMN action_type VARCHAR(128),
    ADD COLUMN action_source VARCHAR(32),
    ADD COLUMN action_actor_type VARCHAR(32),
    ADD COLUMN action_actor_id VARCHAR(128),
    ADD COLUMN action_actor_name VARCHAR(128),
    ADD COLUMN action_trace_id VARCHAR(128),
    ADD COLUMN action_correlation_id VARCHAR(128);

CREATE INDEX idx_oa_execution_action
    ON oa_execution (action_id)
    WHERE action_id IS NOT NULL;
CREATE INDEX idx_oa_execution_step_action
    ON oa_execution_step_attempt (action_id)
    WHERE action_id IS NOT NULL;
CREATE INDEX idx_oa_callback_action
    ON oa_callback_inbox (action_id)
    WHERE action_id IS NOT NULL;
CREATE INDEX idx_oa_audit_action
    ON oa_audit_event (action_id)
    WHERE action_id IS NOT NULL;
