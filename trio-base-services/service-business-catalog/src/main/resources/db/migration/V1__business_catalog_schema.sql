CREATE TABLE IF NOT EXISTS bc_business_object (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    object_type VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    owner_service VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    version INTEGER NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    manifest_json TEXT NOT NULL,
    statuses_json TEXT,
    actions_json TEXT,
    fields_json TEXT,
    page_json TEXT,
    attributes_json TEXT,
    is_tenant_override BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    offline_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bc_business_object_version
    ON bc_business_object (tenant_id, object_type, version);

CREATE INDEX IF NOT EXISTS idx_bc_business_object_tenant
    ON bc_business_object (tenant_id, lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_bc_business_object_owner
    ON bc_business_object (owner_service, object_type);
