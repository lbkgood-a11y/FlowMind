-- Lowcode rapid-application metadata: logical app, immutable versions, page descriptors, and actions.

CREATE TABLE IF NOT EXISTS lc_application (
    id                          VARCHAR(32)  PRIMARY KEY,
    tenant_id                   VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    app_key                     VARCHAR(128) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    description                 VARCHAR(512),
    status                      VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    latest_version              INTEGER      NOT NULL DEFAULT 0,
    latest_published_version_id VARCHAR(32),
    created_by                  VARCHAR(64),
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_application_tenant_key
    ON lc_application(tenant_id, app_key);

CREATE INDEX IF NOT EXISTS idx_lc_application_tenant_status
    ON lc_application(tenant_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS lc_application_version (
    id                           VARCHAR(32)  PRIMARY KEY,
    tenant_id                    VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    application_id               VARCHAR(32)  NOT NULL REFERENCES lc_application(id) ON DELETE CASCADE,
    app_key                      VARCHAR(128) NOT NULL,
    version                      INTEGER      NOT NULL DEFAULT 1,
    status                       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    name                         VARCHAR(255) NOT NULL,
    description                  VARCHAR(512),
    primary_form_definition_id   VARCHAR(32)  NOT NULL REFERENCES lc_form_definition(id),
    form_key                     VARCHAR(128) NOT NULL,
    form_version                 INTEGER      NOT NULL,
    schema_hash                  VARCHAR(64),
    view_permission_code         VARCHAR(256),
    metadata_hash                VARCHAR(64),
    published_at                 TIMESTAMP,
    offline_at                   TIMESTAMP,
    source_application_version_id VARCHAR(32),
    created_by                   VARCHAR(64),
    created_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                   VARCHAR(64),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_application_version_tenant_key_version
    ON lc_application_version(tenant_id, app_key, version);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_application_version_tenant_key_draft
    ON lc_application_version(tenant_id, app_key)
    WHERE status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_lc_application_version_tenant_status
    ON lc_application_version(tenant_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS lc_application_page (
    id                     VARCHAR(32) PRIMARY KEY,
    tenant_id              VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    application_version_id VARCHAR(32) NOT NULL REFERENCES lc_application_version(id) ON DELETE CASCADE,
    page_type              VARCHAR(32) NOT NULL,
    metadata_json          TEXT        NOT NULL,
    sort_order             INTEGER     NOT NULL DEFAULT 0,
    created_by             VARCHAR(64),
    created_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(64),
    updated_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_application_page_version_type
    ON lc_application_page(application_version_id, page_type);

CREATE INDEX IF NOT EXISTS idx_lc_application_page_tenant_version
    ON lc_application_page(tenant_id, application_version_id, sort_order);

CREATE TABLE IF NOT EXISTS lc_application_action (
    id                     VARCHAR(32)  PRIMARY KEY,
    tenant_id              VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    application_version_id VARCHAR(32)  NOT NULL REFERENCES lc_application_version(id) ON DELETE CASCADE,
    action_code            VARCHAR(128) NOT NULL,
    action_type            VARCHAR(64)  NOT NULL,
    label                  VARCHAR(255) NOT NULL,
    permission_code        VARCHAR(256),
    form_definition_id     VARCHAR(32) REFERENCES lc_form_definition(id),
    process_key            VARCHAR(128),
    metadata_json          TEXT,
    status                 VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    sort_order             INTEGER      NOT NULL DEFAULT 0,
    created_by             VARCHAR(64),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(64),
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_application_action_version_code
    ON lc_application_action(application_version_id, action_code);

CREATE INDEX IF NOT EXISTS idx_lc_application_action_tenant_version
    ON lc_application_action(tenant_id, application_version_id, sort_order);
