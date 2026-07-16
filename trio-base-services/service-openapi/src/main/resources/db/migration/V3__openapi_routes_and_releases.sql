CREATE TABLE oa_connector_endpoint (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    connector_key       VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_connector_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_connector_key
    ON oa_connector_endpoint (COALESCE(tenant_id, '__PLATFORM__'), connector_key);

CREATE TABLE oa_connector_version (
    id                      VARCHAR(32) PRIMARY KEY,
    connector_endpoint_id   VARCHAR(32) NOT NULL REFERENCES oa_connector_endpoint(id),
    version_number          INTEGER NOT NULL,
    lifecycle_state         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    base_url                VARCHAR(2048) NOT NULL,
    operation_path          VARCHAR(1024) NOT NULL,
    http_method             VARCHAR(16) NOT NULL,
    timeout_millis          INTEGER NOT NULL,
    operation_class         VARCHAR(32) NOT NULL,
    authentication_type     VARCHAR(32) NOT NULL DEFAULT 'NONE',
    secret_reference        VARCHAR(1024),
    network_policy          JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_size_limit     BIGINT NOT NULL DEFAULT 10485760,
    published_by            VARCHAR(64),
    published_at            TIMESTAMPTZ,
    row_version             BIGINT NOT NULL DEFAULT 0,
    created_by              VARCHAR(64) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_connector_version UNIQUE (connector_endpoint_id, version_number),
    CONSTRAINT ck_oa_connector_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT ck_oa_connector_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD')),
    CONSTRAINT ck_oa_connector_operation CHECK (operation_class IN ('READ_ONLY', 'STATE_CHANGING')),
    CONSTRAINT ck_oa_connector_auth CHECK (authentication_type IN ('NONE', 'API_KEY', 'BASIC', 'OAUTH2_CLIENT', 'HMAC', 'RSA', 'MTLS')),
    CONSTRAINT ck_oa_connector_timeout CHECK (timeout_millis > 0),
    CONSTRAINT ck_oa_connector_response_limit CHECK (response_size_limit > 0)
);

CREATE UNIQUE INDEX uk_oa_connector_single_draft
    ON oa_connector_version (connector_endpoint_id) WHERE lifecycle_state = 'DRAFT';

CREATE TABLE oa_route_definition (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    route_key           VARCHAR(160) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_route_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_route_key
    ON oa_route_definition (COALESCE(tenant_id, '__PLATFORM__'), route_key);

CREATE TABLE oa_route_version (
    id                          VARCHAR(32) PRIMARY KEY,
    route_definition_id         VARCHAR(32) NOT NULL REFERENCES oa_route_definition(id),
    version_number              INTEGER NOT NULL,
    environment                 VARCHAR(16) NOT NULL,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority                    INTEGER NOT NULL DEFAULT 0,
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    route_predicate             JSONB NOT NULL DEFAULT '{}'::jsonb,
    execution_mode              VARCHAR(32) NOT NULL,
    connector_version_id        VARCHAR(32) REFERENCES oa_connector_version(id),
    request_mapping_version_id  VARCHAR(32) REFERENCES oa_mapping_version(id),
    response_mapping_version_id VARCHAR(32) REFERENCES oa_mapping_version(id),
    published_by                VARCHAR(64),
    published_at                TIMESTAMPTZ,
    row_version                 BIGINT NOT NULL DEFAULT 0,
    created_by                  VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64) NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_route_version UNIQUE (route_definition_id, environment, version_number),
    CONSTRAINT ck_oa_route_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_oa_route_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT ck_oa_route_execution_mode CHECK (execution_mode IN ('SYNCHRONOUS', 'ORCHESTRATED')),
    CONSTRAINT ck_oa_route_effective_window CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from)
);

CREATE INDEX idx_oa_route_resolution
    ON oa_route_version (route_definition_id, environment, lifecycle_state, enabled, priority DESC);
CREATE UNIQUE INDEX uk_oa_route_single_draft
    ON oa_route_version (route_definition_id, environment) WHERE lifecycle_state = 'DRAFT';

CREATE TABLE oa_orchestration_definition (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    orchestration_key   VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_orchestration_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_orchestration_key
    ON oa_orchestration_definition (COALESCE(tenant_id, '__PLATFORM__'), orchestration_key);

CREATE TABLE oa_orchestration_version (
    id                          VARCHAR(32) PRIMARY KEY,
    orchestration_definition_id VARCHAR(32) NOT NULL REFERENCES oa_orchestration_definition(id),
    version_number              INTEGER NOT NULL,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    definition_schema_version   VARCHAR(32) NOT NULL,
    definition_content          JSONB NOT NULL,
    definition_hash             VARCHAR(64) NOT NULL,
    validation_result           JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_by                VARCHAR(64),
    published_at                TIMESTAMPTZ,
    row_version                 BIGINT NOT NULL DEFAULT 0,
    created_by                  VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64) NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_orchestration_version UNIQUE (orchestration_definition_id, version_number),
    CONSTRAINT ck_oa_orchestration_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_orchestration_single_draft
    ON oa_orchestration_version (orchestration_definition_id) WHERE lifecycle_state = 'DRAFT';

ALTER TABLE oa_route_version
    ADD COLUMN orchestration_version_id VARCHAR(32) REFERENCES oa_orchestration_version(id);

CREATE TABLE oa_release_snapshot (
    id                          VARCHAR(32) PRIMARY KEY,
    tenant_id                   VARCHAR(32),
    environment                 VARCHAR(16) NOT NULL,
    route_definition_id         VARCHAR(32) NOT NULL REFERENCES oa_route_definition(id),
    route_version_id            VARCHAR(32) NOT NULL REFERENCES oa_route_version(id),
    release_number              INTEGER NOT NULL,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    pinned_dependencies         JSONB NOT NULL,
    snapshot_hash               VARCHAR(64) NOT NULL,
    validation_result           JSONB NOT NULL DEFAULT '{}'::jsonb,
    release_notes               VARCHAR(2048),
    published_by                VARCHAR(64) NOT NULL,
    published_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deprecated_at               TIMESTAMPTZ,
    CONSTRAINT uk_oa_release_number UNIQUE (route_definition_id, environment, release_number),
    CONSTRAINT ck_oa_release_environment CHECK (environment IN ('DEV', 'TEST', 'PROD')),
    CONSTRAINT ck_oa_release_state CHECK (lifecycle_state IN ('PUBLISHED', 'DEPRECATED', 'ARCHIVED'))
);

CREATE INDEX idx_oa_release_route ON oa_release_snapshot (route_definition_id, environment, lifecycle_state);

CREATE TABLE oa_active_release (
    route_definition_id VARCHAR(32) NOT NULL REFERENCES oa_route_definition(id),
    environment         VARCHAR(16) NOT NULL,
    release_snapshot_id VARCHAR(32) NOT NULL REFERENCES oa_release_snapshot(id),
    policy_version      BIGINT NOT NULL DEFAULT 1,
    activated_by        VARCHAR(64) NOT NULL,
    activated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (route_definition_id, environment),
    CONSTRAINT ck_oa_active_release_environment CHECK (environment IN ('DEV', 'TEST', 'PROD'))
);
