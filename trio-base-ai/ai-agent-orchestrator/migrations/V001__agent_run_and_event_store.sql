CREATE TABLE IF NOT EXISTS ai_agent_run (
    run_id VARCHAR(64) PRIMARY KEY,
    thread_id VARCHAR(128) NOT NULL,
    graph_id VARCHAR(128) NOT NULL,
    graph_version VARCHAR(64) NOT NULL,
    state_schema_version VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    pending_interrupt JSONB,
    action_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    error JSONB,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_ai_agent_run_idempotency UNIQUE (tenant_id, actor_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_run_thread
    ON ai_agent_run (tenant_id, actor_id, thread_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_retention ON ai_agent_run (updated_at);

CREATE TABLE IF NOT EXISTS ai_agent_event (
    event_id VARCHAR(96) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES ai_agent_run(run_id) ON DELETE CASCADE,
    thread_id VARCHAR(128) NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    data_schema_version VARCHAR(32) NOT NULL,
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_ai_agent_event_sequence UNIQUE (run_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_replay ON ai_agent_event (run_id, sequence);
