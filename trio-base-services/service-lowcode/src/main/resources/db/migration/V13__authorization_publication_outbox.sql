ALTER TABLE lc_form_definition
    ADD COLUMN IF NOT EXISTS authorization_status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS authorization_snapshot_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS authorization_revision BIGINT,
    ADD COLUMN IF NOT EXISTS authorization_synced_at TIMESTAMP;

UPDATE lc_form_definition
SET authorization_status = CASE
    WHEN status IN ('PUBLISHED', 'OFFLINE') THEN 'SYNCED'
    ELSE 'NOT_REQUIRED'
END
WHERE authorization_status IS NULL;

ALTER TABLE lc_form_definition
    ALTER COLUMN authorization_status SET DEFAULT 'NOT_REQUIRED',
    ALTER COLUMN authorization_status SET NOT NULL;

ALTER TABLE lc_application_version
    ADD COLUMN IF NOT EXISTS authorization_status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS authorization_snapshot_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS authorization_revision BIGINT,
    ADD COLUMN IF NOT EXISTS authorization_synced_at TIMESTAMP;

UPDATE lc_application_version
SET authorization_status = CASE
    WHEN status IN ('PUBLISHED', 'OFFLINE') THEN 'SYNCED'
    ELSE 'NOT_REQUIRED'
END
WHERE authorization_status IS NULL;

ALTER TABLE lc_application_version
    ALTER COLUMN authorization_status SET DEFAULT 'NOT_REQUIRED',
    ALTER COLUMN authorization_status SET NOT NULL;

CREATE TABLE IF NOT EXISTS lc_authorization_outbox (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(24) NOT NULL,
    aggregate_id VARCHAR(32) NOT NULL,
    aggregate_version INTEGER NOT NULL,
    operation VARCHAR(16) NOT NULL,
    snapshot_hash VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    locked_at TIMESTAMP,
    acknowledged_revision BIGINT,
    acknowledged_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lc_auth_outbox_dispatch
    ON lc_authorization_outbox(status, next_retry_at, created_at);
CREATE INDEX IF NOT EXISTS idx_lc_auth_outbox_aggregate
    ON lc_authorization_outbox(tenant_id, aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lc_form_auth_status
    ON lc_form_definition(tenant_id, authorization_status, status);
CREATE INDEX IF NOT EXISTS idx_lc_app_auth_status
    ON lc_application_version(tenant_id, authorization_status, status);
