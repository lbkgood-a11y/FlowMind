CREATE TABLE IF NOT EXISTS act_document_timeline_event (
    id VARCHAR(64) PRIMARY KEY,
    event_source VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(128) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    display_name VARCHAR(256),
    actor_id VARCHAR(128),
    actor_name VARCHAR(128),
    action_id VARCHAR(64),
    action_type VARCHAR(160),
    action_status VARCHAR(32),
    owner_service VARCHAR(128),
    owner_execution_ref VARCHAR(256),
    trace_id VARCHAR(128),
    correlation_id VARCHAR(128),
    summary_json TEXT,
    redacted BOOLEAN NOT NULL DEFAULT TRUE,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_act_document_timeline_target
    ON act_document_timeline_event (tenant_id, target_type, target_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_act_document_timeline_action
    ON act_document_timeline_event (action_id);

CREATE INDEX IF NOT EXISTS idx_act_document_timeline_trace
    ON act_document_timeline_event (trace_id);

CREATE INDEX IF NOT EXISTS idx_act_document_timeline_correlation
    ON act_document_timeline_event (correlation_id);

CREATE INDEX IF NOT EXISTS idx_act_document_timeline_owner_ref
    ON act_document_timeline_event (owner_execution_ref);
