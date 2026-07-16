CREATE TABLE oa_idempotency_record (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    environment         VARCHAR(16) NOT NULL,
    application_client_id VARCHAR(32),
    route_definition_id VARCHAR(32) NOT NULL REFERENCES oa_route_definition(id),
    release_snapshot_id VARCHAR(32) NOT NULL REFERENCES oa_release_snapshot(id),
    idempotency_key     VARCHAR(256) NOT NULL,
    request_hash        VARCHAR(64) NOT NULL,
    execution_id        VARCHAR(32),
    record_state        VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    response_reference  VARCHAR(1024),
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_idempotency UNIQUE (tenant_id, environment, route_definition_id, release_snapshot_id, idempotency_key),
    CONSTRAINT ck_oa_idempotency_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_oa_idempotency_state CHECK (record_state IN ('RESERVED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED'))
);

CREATE INDEX idx_oa_idempotency_expiry ON oa_idempotency_record (expires_at);

CREATE TABLE oa_execution (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    environment           VARCHAR(16) NOT NULL,
    application_client_id VARCHAR(32),
    route_definition_id   VARCHAR(32) NOT NULL REFERENCES oa_route_definition(id),
    release_snapshot_id   VARCHAR(32) NOT NULL REFERENCES oa_release_snapshot(id),
    execution_mode        VARCHAR(32) NOT NULL,
    execution_state       VARCHAR(32) NOT NULL,
    workflow_id           VARCHAR(256),
    workflow_run_id       VARCHAR(256),
    idempotency_key       VARCHAR(256),
    trace_id              VARCHAR(128),
    caller_id             VARCHAR(128),
    started_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMPTZ,
    duration_millis       BIGINT,
    error_code            VARCHAR(128),
    sanitized_error       VARCHAR(2048),
    diagnostic_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    diagnostic_expires_at TIMESTAMPTZ,
    retention_until       TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '180 days'),
    row_version           BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_execution_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_oa_execution_mode CHECK (execution_mode IN ('SYNCHRONOUS', 'ORCHESTRATED')),
    CONSTRAINT ck_oa_execution_state CHECK (execution_state IN ('ACCEPTED', 'RUNNING', 'WAITING_CALLBACK', 'SUCCEEDED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'CANCELLED', 'QUARANTINED')),
    CONSTRAINT ck_oa_execution_diagnostic_expiry CHECK (diagnostic_expires_at IS NULL OR diagnostic_expires_at <= created_at + INTERVAL '7 days')
);

ALTER TABLE oa_idempotency_record
    ADD CONSTRAINT fk_oa_idempotency_execution FOREIGN KEY (execution_id) REFERENCES oa_execution(id);

CREATE INDEX idx_oa_execution_tenant_time ON oa_execution (tenant_id, started_at DESC);
CREATE INDEX idx_oa_execution_route_state ON oa_execution (route_definition_id, environment, execution_state);
CREATE INDEX idx_oa_execution_trace ON oa_execution (trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_oa_execution_workflow ON oa_execution (workflow_id) WHERE workflow_id IS NOT NULL;
CREATE INDEX idx_oa_execution_retention ON oa_execution (retention_until);

CREATE TABLE oa_execution_step_attempt (
    id                  VARCHAR(32) PRIMARY KEY,
    execution_id        VARCHAR(32) NOT NULL REFERENCES oa_execution(id) ON DELETE CASCADE,
    step_key            VARCHAR(256) NOT NULL,
    step_type           VARCHAR(32) NOT NULL,
    attempt_number      INTEGER NOT NULL,
    attempt_state       VARCHAR(32) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMPTZ,
    duration_millis     BIGINT,
    external_status     INTEGER,
    error_code          VARCHAR(128),
    sanitized_error     VARCHAR(2048),
    evidence            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_execution_step_attempt UNIQUE (execution_id, step_key, attempt_number),
    CONSTRAINT ck_oa_execution_step_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_oa_execution_step_state CHECK (attempt_state IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED', 'COMPENSATED'))
);

CREATE INDEX idx_oa_execution_step_execution ON oa_execution_step_attempt (execution_id, started_at);

CREATE TABLE oa_audit_event (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    actor_id            VARCHAR(128) NOT NULL,
    actor_type          VARCHAR(32) NOT NULL,
    action              VARCHAR(128) NOT NULL,
    resource_type       VARCHAR(128) NOT NULL,
    resource_id         VARCHAR(64),
    environment         VARCHAR(16),
    outcome             VARCHAR(32) NOT NULL,
    reason              VARCHAR(1024),
    trace_id            VARCHAR(128),
    source_ip           VARCHAR(128),
    change_summary      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_audit_actor_type CHECK (actor_type IN ('USER', 'APPLICATION', 'SYSTEM')),
    CONSTRAINT ck_oa_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED')),
    CONSTRAINT ck_oa_audit_environment CHECK (environment IS NULL OR environment IN ('DEV', 'TEST', 'PROD'))
);

CREATE INDEX idx_oa_audit_tenant_time ON oa_audit_event (tenant_id, created_at DESC);
CREATE INDEX idx_oa_audit_resource ON oa_audit_event (resource_type, resource_id, created_at DESC);
CREATE INDEX idx_oa_audit_trace ON oa_audit_event (trace_id) WHERE trace_id IS NOT NULL;
