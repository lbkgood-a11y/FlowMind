CREATE TABLE IF NOT EXISTS sys_auth_bundle_receipt (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    application_resource_code VARCHAR(256) NOT NULL,
    preset VARCHAR(32) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    grant_count INTEGER NOT NULL,
    authorization_version BIGINT NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_bundle_receipt_key UNIQUE (tenant_id, idempotency_key)
);

