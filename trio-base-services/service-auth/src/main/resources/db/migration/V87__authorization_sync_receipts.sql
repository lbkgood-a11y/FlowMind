CREATE TABLE IF NOT EXISTS sys_auth_sync_receipt (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_service VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(24) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version INTEGER NOT NULL,
    operation VARCHAR(16) NOT NULL,
    snapshot_hash VARCHAR(128) NOT NULL,
    resource_version BIGINT NOT NULL,
    resource_codes_json TEXT NOT NULL,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_auth_sync_receipt_event UNIQUE (tenant_id, owner_service, event_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_auth_sync_receipt_aggregate
    ON sys_auth_sync_receipt(
        tenant_id, owner_service, aggregate_type, aggregate_id, aggregate_version DESC
    );
