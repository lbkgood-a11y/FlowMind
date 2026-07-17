CREATE TABLE oa_callback_profile (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    callback_key        VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_callback_profile_key UNIQUE (callback_key),
    CONSTRAINT ck_oa_callback_profile_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_oa_callback_profile_tenant ON oa_callback_profile (tenant_id, lifecycle_state);

CREATE TABLE oa_callback_profile_version (
    id                          VARCHAR(32) PRIMARY KEY,
    callback_profile_id         VARCHAR(32) NOT NULL REFERENCES oa_callback_profile(id),
    version_number              INTEGER NOT NULL,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    environment                 VARCHAR(16) NOT NULL,
    application_client_id       VARCHAR(32) NOT NULL REFERENCES oa_application_client(id),
    authentication_type         VARCHAR(32) NOT NULL,
    secret_reference            VARCHAR(512),
    request_structure_version_id VARCHAR(32) NOT NULL REFERENCES oa_structure_version(id),
    inbound_mapping_version_id  VARCHAR(32) REFERENCES oa_mapping_version(id),
    partner_event_id_pointer    VARCHAR(512) NOT NULL,
    correlation_pointer         VARCHAR(512) NOT NULL,
    correlation_type            VARCHAR(32) NOT NULL,
    signal_name                 VARCHAR(128) NOT NULL,
    replay_window_seconds       BIGINT NOT NULL DEFAULT 300,
    max_body_bytes              BIGINT NOT NULL DEFAULT 1048576,
    callback_per_minute         BIGINT NOT NULL DEFAULT 60,
    acknowledgement_status      INTEGER NOT NULL DEFAULT 202,
    acknowledgement_content_type VARCHAR(128) NOT NULL DEFAULT 'application/json',
    acknowledgement_body        VARCHAR(2048) NOT NULL DEFAULT '{"accepted":true}',
    security_policy             JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_result           JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_by                VARCHAR(64),
    published_at                TIMESTAMPTZ,
    row_version                 BIGINT NOT NULL DEFAULT 0,
    created_by                  VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64) NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_callback_profile_version UNIQUE (callback_profile_id, version_number),
    CONSTRAINT ck_oa_callback_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT ck_oa_callback_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_oa_callback_auth CHECK (authentication_type IN ('API_KEY', 'BASIC', 'OAUTH2_CLIENT', 'HMAC', 'RSA', 'MTLS')),
    CONSTRAINT ck_oa_callback_correlation_type CHECK (correlation_type IN ('EXECUTION_ID', 'WORKFLOW_ID', 'IDEMPOTENCY_KEY')),
    CONSTRAINT ck_oa_callback_replay_window CHECK (replay_window_seconds BETWEEN 30 AND 86400),
    CONSTRAINT ck_oa_callback_body_limit CHECK (max_body_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_oa_callback_rate CHECK (callback_per_minute > 0),
    CONSTRAINT ck_oa_callback_ack_status CHECK (acknowledgement_status BETWEEN 200 AND 299)
);

CREATE UNIQUE INDEX uk_oa_callback_single_draft
    ON oa_callback_profile_version (callback_profile_id) WHERE lifecycle_state = 'DRAFT';
CREATE INDEX idx_oa_callback_published_lookup
    ON oa_callback_profile_version (callback_profile_id, environment, lifecycle_state, version_number DESC);

CREATE TABLE oa_callback_nonce (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    application_client_id VARCHAR(32) NOT NULL REFERENCES oa_application_client(id),
    callback_profile_version_id VARCHAR(32) NOT NULL REFERENCES oa_callback_profile_version(id),
    nonce               VARCHAR(256) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_callback_nonce UNIQUE (
        tenant_id, application_client_id, callback_profile_version_id, nonce)
);

CREATE INDEX idx_oa_callback_nonce_expiry ON oa_callback_nonce (expires_at);

CREATE TABLE oa_callback_inbox (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    application_client_id VARCHAR(32) NOT NULL REFERENCES oa_application_client(id),
    callback_profile_version_id VARCHAR(32) NOT NULL REFERENCES oa_callback_profile_version(id),
    partner_event_id    VARCHAR(256) NOT NULL,
    correlation_value   VARCHAR(512) NOT NULL,
    execution_id        VARCHAR(32) REFERENCES oa_execution(id) ON DELETE SET NULL,
    inbox_state         VARCHAR(32) NOT NULL,
    body_hash           VARCHAR(64) NOT NULL,
    mapped_payload      JSONB NOT NULL DEFAULT '{}'::jsonb,
    signal_name         VARCHAR(128) NOT NULL,
    signal_attempts     INTEGER NOT NULL DEFAULT 0,
    next_signal_at      TIMESTAMPTZ,
    last_signal_error   VARCHAR(1024),
    quarantine_reason   VARCHAR(128),
    resolution_state    VARCHAR(32),
    resolution_note     VARCHAR(1024),
    resolved_by         VARCHAR(64),
    resolved_at         TIMESTAMPTZ,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_until     TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '180 days'),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_callback_event UNIQUE (
        tenant_id, application_client_id, callback_profile_version_id, partner_event_id),
    CONSTRAINT ck_oa_callback_inbox_state CHECK (inbox_state IN ('SIGNAL_PENDING', 'SIGNALING', 'SIGNALLED', 'QUARANTINED', 'FAILED')),
    CONSTRAINT ck_oa_callback_resolution_state CHECK (resolution_state IS NULL OR resolution_state IN ('RETRY', 'DISCARD', 'LINKED'))
);

CREATE INDEX idx_oa_callback_signal_pending
    ON oa_callback_inbox (inbox_state, next_signal_at, received_at)
    WHERE inbox_state = 'SIGNAL_PENDING';
CREATE INDEX idx_oa_callback_quarantine
    ON oa_callback_inbox (tenant_id, inbox_state, received_at DESC)
    WHERE inbox_state = 'QUARANTINED';
CREATE INDEX idx_oa_callback_execution ON oa_callback_inbox (execution_id, received_at DESC);
