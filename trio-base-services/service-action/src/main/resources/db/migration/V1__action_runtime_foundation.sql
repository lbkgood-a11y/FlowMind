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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_act_action_event_execution
        FOREIGN KEY (action_id) REFERENCES act_action_execution (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS act_action_definition_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    action_type VARCHAR(160) NOT NULL,
    owner_service VARCHAR(128) NOT NULL,
    target_type VARCHAR(128),
    version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    definition_json TEXT NOT NULL,
    schema_hash VARCHAR(128),
    published_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS act_action_dispatch (
    id VARCHAR(64) PRIMARY KEY,
    action_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_service VARCHAR(128) NOT NULL,
    owner_endpoint VARCHAR(256),
    dispatch_status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    next_retry_at TIMESTAMP,
    last_error VARCHAR(1024),
    locked_by VARCHAR(128),
    locked_at TIMESTAMP,
    dispatched_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_act_action_dispatch_execution
        FOREIGN KEY (action_id) REFERENCES act_action_execution (id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_act_action_execution_idempotency
    ON act_action_execution (tenant_id, action_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_act_action_execution_tenant
    ON act_action_execution (tenant_id);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_type
    ON act_action_execution (action_type);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_actor
    ON act_action_execution (tenant_id, actor_type, actor_id);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_source
    ON act_action_execution (tenant_id, source);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_target
    ON act_action_execution (tenant_id, target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_status
    ON act_action_execution (tenant_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_trace
    ON act_action_execution (trace_id);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_correlation
    ON act_action_execution (correlation_id);

CREATE INDEX IF NOT EXISTS idx_act_action_execution_request
    ON act_action_execution (request_id);

CREATE INDEX IF NOT EXISTS idx_act_action_event_action_sequence
    ON act_action_event (action_id, sequence_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_act_action_event_action_sequence
    ON act_action_event (action_id, sequence_no);

CREATE INDEX IF NOT EXISTS idx_act_action_event_tenant_status
    ON act_action_event (tenant_id, status, occurred_at);

CREATE INDEX IF NOT EXISTS idx_act_action_event_trace
    ON act_action_event (trace_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_act_action_definition_snapshot_version
    ON act_action_definition_snapshot (action_type, version);

CREATE INDEX IF NOT EXISTS idx_act_action_definition_snapshot_owner
    ON act_action_definition_snapshot (owner_service, action_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_act_action_dispatch_action
    ON act_action_dispatch (action_id);

CREATE INDEX IF NOT EXISTS idx_act_action_dispatch_status_retry
    ON act_action_dispatch (dispatch_status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_act_action_dispatch_owner
    ON act_action_dispatch (owner_service, dispatch_status);
