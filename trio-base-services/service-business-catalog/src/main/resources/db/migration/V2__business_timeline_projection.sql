CREATE TABLE IF NOT EXISTS bc_document_timeline_event (
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

CREATE INDEX IF NOT EXISTS idx_bc_document_timeline_target
    ON bc_document_timeline_event (tenant_id, target_type, target_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_bc_document_timeline_action
    ON bc_document_timeline_event (tenant_id, action_id, occurred_at DESC)
    WHERE action_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bc_document_timeline_trace
    ON bc_document_timeline_event (tenant_id, trace_id, occurred_at DESC)
    WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bc_document_timeline_correlation
    ON bc_document_timeline_event (tenant_id, correlation_id, occurred_at DESC)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS act_action_execution (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(160) NOT NULL,
    source VARCHAR(32) NOT NULL,
    actor_type VARCHAR(32),
    actor_id VARCHAR(128),
    actor_name VARCHAR(128),
    target_type VARCHAR(128),
    target_id VARCHAR(128),
    target_owner_service VARCHAR(128),
    target_tenant_id VARCHAR(64),
    target_version VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    execution_mode VARCHAR(32),
    audit_level VARCHAR(32),
    idempotency_key VARCHAR(256),
    correlation_id VARCHAR(128),
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    owner_service VARCHAR(128),
    owner_execution_ref VARCHAR(256),
    payload_summary TEXT,
    result_summary TEXT,
    error_summary TEXT,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS act_action_event (
    id VARCHAR(64) PRIMARY KEY,
    action_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32),
    sequence_no INTEGER NOT NULL,
    message VARCHAR(512),
    event_data_json TEXT,
    trace_id VARCHAR(128),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE INDEX IF NOT EXISTS idx_bc_act_action_execution_target
    ON act_action_execution (tenant_id, target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_bc_act_action_execution_trace
    ON act_action_execution (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bc_act_action_execution_correlation
    ON act_action_execution (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bc_act_action_event_action_sequence
    ON act_action_event (action_id, sequence_no);

CREATE INDEX IF NOT EXISTS idx_bc_act_document_timeline_target
    ON act_document_timeline_event (tenant_id, target_type, target_id, occurred_at DESC);
