CREATE TABLE oa_policy_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32),
    environment VARCHAR(16) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    policy_content JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    signature VARCHAR(2048) NOT NULL,
    published_by VARCHAR(64) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_policy_snapshot_environment CHECK (environment IN ('DEV','TEST','PROD'))
);
CREATE UNIQUE INDEX uk_oa_policy_snapshot_version ON oa_policy_snapshot(
    COALESCE(tenant_id,'__PLATFORM__'), environment, snapshot_version);
CREATE INDEX idx_oa_policy_snapshot_latest ON oa_policy_snapshot(tenant_id, environment, snapshot_version DESC);
