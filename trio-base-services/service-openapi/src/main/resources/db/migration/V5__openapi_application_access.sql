CREATE TABLE oa_api_scope (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32),
    scope_key VARCHAR(160) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_api_scope_state CHECK (lifecycle_state IN ('ACTIVE','ARCHIVED'))
);
CREATE UNIQUE INDEX uk_oa_api_scope_key ON oa_api_scope(COALESCE(tenant_id,'__PLATFORM__'), scope_key);

CREATE TABLE oa_api_product (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32),
    product_key VARCHAR(160) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    audience VARCHAR(1024),
    risk_level VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'TENANT',
    documentation TEXT,
    terms TEXT,
    default_scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_traffic_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    default_security_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_product_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_oa_product_visibility CHECK (visibility IN ('PRIVATE','TENANT','PLATFORM_PUBLIC')),
    CONSTRAINT ck_oa_product_state CHECK (lifecycle_state IN ('ACTIVE','ARCHIVED'))
);
CREATE UNIQUE INDEX uk_oa_product_key ON oa_api_product(COALESCE(tenant_id,'__PLATFORM__'), product_key);

CREATE TABLE oa_api_product_access_grant (
    id VARCHAR(32) PRIMARY KEY,
    api_product_id VARCHAR(32) NOT NULL REFERENCES oa_api_product(id) ON DELETE CASCADE,
    grantee_type VARCHAR(32) NOT NULL,
    grantee_id VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_product_grantee CHECK (grantee_type IN ('APPLICATION','ORGANIZATION')),
    CONSTRAINT uk_oa_product_grant UNIQUE(api_product_id, grantee_type, grantee_id)
);

CREATE TABLE oa_api_product_version (
    id VARCHAR(32) PRIMARY KEY,
    api_product_id VARCHAR(32) NOT NULL REFERENCES oa_api_product(id),
    semantic_version VARCHAR(32) NOT NULL,
    major_version INTEGER NOT NULL, minor_version INTEGER NOT NULL, patch_version INTEGER NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    documentation TEXT, terms TEXT,
    pinned_routes JSONB NOT NULL DEFAULT '[]'::jsonb,
    pinned_contracts JSONB NOT NULL DEFAULT '[]'::jsonb,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    traffic_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    security_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    change_classification VARCHAR(16) NOT NULL,
    migration_notice TEXT,
    validation_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_by VARCHAR(64), published_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_product_semver UNIQUE(api_product_id, semantic_version),
    CONSTRAINT ck_oa_product_version_state CHECK (lifecycle_state IN ('DRAFT','PUBLISHED','DEPRECATED','ARCHIVED')),
    CONSTRAINT ck_oa_product_change CHECK (change_classification IN ('PATCH','MINOR','MAJOR')),
    CONSTRAINT ck_oa_product_semver_parts CHECK (major_version >= 0 AND minor_version >= 0 AND patch_version >= 0)
);
CREATE UNIQUE INDEX uk_oa_product_single_draft ON oa_api_product_version(api_product_id) WHERE lifecycle_state='DRAFT';

CREATE TABLE oa_api_product_route_member (
    id VARCHAR(32) PRIMARY KEY,
    api_product_version_id VARCHAR(32) NOT NULL REFERENCES oa_api_product_version(id) ON DELETE CASCADE,
    route_key VARCHAR(160) NOT NULL,
    release_snapshot_id VARCHAR(32) NOT NULL REFERENCES oa_release_snapshot(id),
    operations JSONB NOT NULL DEFAULT '[]'::jsonb,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    canonical_structure_version_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_product_route UNIQUE(api_product_version_id, route_key)
);

CREATE TABLE oa_application (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    application_key VARCHAR(160) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    purpose VARCHAR(2048) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    suspension_reason VARCHAR(1024),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_application_key UNIQUE(tenant_id, application_key),
    CONSTRAINT ck_oa_application_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_oa_application_state CHECK (lifecycle_state IN ('DRAFT','PENDING_APPROVAL','APPROVED','ACTIVE','SUSPENDED','EXPIRED','REVOKED','ARCHIVED'))
);

CREATE TABLE oa_application_owner (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(32) NOT NULL REFERENCES oa_application(id) ON DELETE CASCADE,
    owner_id VARCHAR(64) NOT NULL,
    owner_role VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_application_owner UNIQUE(application_id, owner_id, owner_role)
);

CREATE TABLE oa_application_contact (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(32) NOT NULL REFERENCES oa_application(id) ON DELETE CASCADE,
    contact_role VARCHAR(64) NOT NULL,
    contact_name VARCHAR(256) NOT NULL,
    email VARCHAR(320), phone VARCHAR(64),
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_application_client (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(32) NOT NULL REFERENCES oa_application(id),
    tenant_id VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    client_key VARCHAR(160) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    network_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    security_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ,
    suspension_reason VARCHAR(1024),
    security_violation_count INTEGER NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_application_environment UNIQUE(application_id, environment),
    CONSTRAINT uk_oa_client_key UNIQUE(tenant_id, environment, client_key),
    CONSTRAINT ck_oa_client_environment CHECK (environment IN ('DEV','TEST','PROD')),
    CONSTRAINT ck_oa_client_state CHECK (lifecycle_state IN ('DRAFT','PENDING_APPROVAL','APPROVED','ACTIVE','SUSPENDED','EXPIRED','REVOKED'))
);
CREATE INDEX idx_oa_client_admission ON oa_application_client(tenant_id, environment, client_key, lifecycle_state);

CREATE TABLE oa_asset_approval (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32),
    asset_type VARCHAR(64) NOT NULL,
    asset_id VARCHAR(32) NOT NULL,
    environment VARCHAR(16),
    approval_role VARCHAR(64) NOT NULL,
    submitted_by VARCHAR(64) NOT NULL,
    decided_by VARCHAR(64),
    decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    decided_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_approval_decision CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT uk_oa_asset_approval UNIQUE(asset_type, asset_id, approval_role)
);

CREATE TABLE oa_credential_binding (
    id VARCHAR(32) PRIMARY KEY,
    application_client_id VARCHAR(32) NOT NULL REFERENCES oa_application_client(id),
    authentication_type VARCHAR(32) NOT NULL,
    credential_version INTEGER NOT NULL,
    secret_reference VARCHAR(1024) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    retirement_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    one_time_secret_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_credential_version UNIQUE(application_client_id, credential_version),
    CONSTRAINT ck_oa_credential_state CHECK (lifecycle_state IN ('ACTIVE','RETIRING','RETIRED','REVOKED','EXPIRED')),
    CONSTRAINT ck_oa_credential_auth CHECK (authentication_type IN ('API_KEY','BASIC','OAUTH2_CLIENT','HMAC','RSA','MTLS'))
);
CREATE INDEX idx_oa_credential_active ON oa_credential_binding(application_client_id, lifecycle_state, valid_from, expires_at);

CREATE TABLE oa_product_subscription (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    application_client_id VARCHAR(32) NOT NULL REFERENCES oa_application_client(id),
    api_product_version_id VARCHAR(32) NOT NULL REFERENCES oa_api_product_version(id),
    environment VARCHAR(16) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    requested_scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    effective_scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    effective_from TIMESTAMPTZ,
    effective_until TIMESTAMPTZ,
    requested_by VARCHAR(64) NOT NULL,
    activated_at TIMESTAMPTZ,
    suspension_reason VARCHAR(1024),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_subscription_environment CHECK (environment IN ('DEV','TEST','PROD')),
    CONSTRAINT ck_oa_subscription_state CHECK (lifecycle_state IN ('REQUESTED','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
    CONSTRAINT ck_oa_subscription_window CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from)
);
CREATE UNIQUE INDEX uk_oa_subscription_active_version ON oa_product_subscription(application_client_id, api_product_version_id)
    WHERE lifecycle_state IN ('REQUESTED','PENDING_APPROVAL','ACTIVE','SUSPENDED');

CREATE TABLE oa_subscription_route_override (
    id VARCHAR(32) PRIMARY KEY,
    subscription_id VARCHAR(32) NOT NULL REFERENCES oa_product_subscription(id) ON DELETE CASCADE,
    route_key VARCHAR(160) NOT NULL,
    excluded BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_operations JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    quota_override JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_networks JSONB NOT NULL DEFAULT '[]'::jsonb,
    structure_version_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    field_restrictions JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_subscription_route_override UNIQUE(subscription_id, route_key)
);

CREATE TABLE oa_traffic_policy_version (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32),
    environment VARCHAR(16) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(160) NOT NULL,
    policy_version BIGINT NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    policy_content JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    signature VARCHAR(2048),
    published_by VARCHAR(64), published_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_policy_environment CHECK (environment IN ('DEV','TEST','PROD')),
    CONSTRAINT ck_oa_policy_scope CHECK (scope_type IN ('TENANT','CLIENT','PRODUCT','ROUTE','OPERATION','SUBSCRIPTION')),
    CONSTRAINT ck_oa_policy_state CHECK (lifecycle_state IN ('DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'))
);
CREATE UNIQUE INDEX uk_oa_policy_version ON oa_traffic_policy_version(
    COALESCE(tenant_id,'__PLATFORM__'), environment, scope_type, scope_id, policy_version);
CREATE INDEX idx_oa_policy_resolution ON oa_traffic_policy_version(tenant_id, environment, scope_type, scope_id, lifecycle_state, policy_version DESC);

CREATE TABLE oa_policy_enforcement_state (
    enforcement_point VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(32) NOT NULL DEFAULT '__PLATFORM__',
    environment VARCHAR(16) NOT NULL,
    required_policy_version BIGINT NOT NULL,
    applied_policy_version BIGINT NOT NULL DEFAULT 0,
    last_reported_at TIMESTAMPTZ,
    drift_state VARCHAR(16) NOT NULL DEFAULT 'LAGGING',
    PRIMARY KEY(enforcement_point, environment, tenant_id),
    CONSTRAINT ck_oa_enforcement_drift CHECK (drift_state IN ('CURRENT','LAGGING','UNKNOWN'))
);
