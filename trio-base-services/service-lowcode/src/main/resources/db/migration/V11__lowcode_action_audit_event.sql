CREATE TABLE IF NOT EXISTS lc_action_audit_event (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    action_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(160),
    event_type VARCHAR(64) NOT NULL,
    action_status VARCHAR(32),
    action_source VARCHAR(32),
    actor_type VARCHAR(32),
    actor_id VARCHAR(128),
    actor_name VARCHAR(128),
    target_type VARCHAR(128),
    target_id VARCHAR(128),
    target_owner_service VARCHAR(128),
    owner_service VARCHAR(128),
    idempotency_key VARCHAR(256),
    trace_id VARCHAR(128),
    correlation_id VARCHAR(128),
    owner_execution_ref VARCHAR(256),
    message VARCHAR(512),
    event_data_json TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_action_audit_event_id
    ON lc_action_audit_event(event_id);

CREATE INDEX IF NOT EXISTS idx_lc_action_audit_action
    ON lc_action_audit_event(tenant_id, action_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_action_audit_target
    ON lc_action_audit_event(tenant_id, target_type, target_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_action_audit_trace
    ON lc_action_audit_event(tenant_id, trace_id, occurred_at DESC)
    WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lc_action_audit_correlation
    ON lc_action_audit_event(tenant_id, correlation_id, occurred_at DESC)
    WHERE correlation_id IS NOT NULL;
