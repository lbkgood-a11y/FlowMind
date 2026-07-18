-- Lowcode production baseline: schema ownership, tenant isolation, versioning, and process binding.

-- ========== lc_form_definition ==========
ALTER TABLE lc_form_definition
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS schema_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS offline_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS source_form_definition_id VARCHAR(32);

UPDATE lc_form_definition
SET tenant_id = COALESCE(NULLIF(TRIM(tenant_id), ''), 'GLOBAL'),
    version = COALESCE(version, 1),
    status = UPPER(COALESCE(NULLIF(TRIM(status), ''), 'DRAFT')),
    schema_hash = COALESCE(schema_hash, md5(COALESCE(schema_json, '') || ':' || COALESCE(ui_schema_json, ''))),
    created_by = COALESCE(NULLIF(TRIM(created_by), ''), 'SYSTEM'),
    updated_by = COALESCE(NULLIF(TRIM(updated_by), ''), NULLIF(TRIM(created_by), ''), 'SYSTEM'),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

UPDATE lc_form_definition
SET published_at = COALESCE(published_at, updated_at, created_at, CURRENT_TIMESTAMP)
WHERE status = 'PUBLISHED';

UPDATE lc_form_definition
SET offline_at = COALESCE(offline_at, updated_at, CURRENT_TIMESTAMP)
WHERE status = 'OFFLINE';

DROP INDEX IF EXISTS uk_lc_form_definition_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_definition_tenant_key_version
    ON lc_form_definition(tenant_id, form_key, version);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_definition_tenant_key_draft
    ON lc_form_definition(tenant_id, form_key)
    WHERE status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_lc_form_definition_tenant_status
    ON lc_form_definition(tenant_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_form_definition_tenant_key
    ON lc_form_definition(tenant_id, form_key, version DESC);

-- ========== lc_form_field_definition ==========
ALTER TABLE lc_form_field_definition
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'GLOBAL';

UPDATE lc_form_field_definition field
SET tenant_id = COALESCE(NULLIF(TRIM(field.tenant_id), ''), definition.tenant_id, 'GLOBAL'),
    created_by = COALESCE(NULLIF(TRIM(field.created_by), ''), 'SYSTEM'),
    updated_by = COALESCE(NULLIF(TRIM(field.updated_by), ''), NULLIF(TRIM(field.created_by), ''), 'SYSTEM'),
    updated_at = COALESCE(field.updated_at, field.created_at, CURRENT_TIMESTAMP)
FROM lc_form_definition definition
WHERE field.form_definition_id = definition.id;

UPDATE lc_form_field_definition
SET tenant_id = COALESCE(NULLIF(TRIM(tenant_id), ''), 'GLOBAL'),
    created_by = COALESCE(NULLIF(TRIM(created_by), ''), 'SYSTEM'),
    updated_by = COALESCE(NULLIF(TRIM(updated_by), ''), NULLIF(TRIM(created_by), ''), 'SYSTEM'),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

CREATE INDEX IF NOT EXISTS idx_lc_form_field_definition_tenant_form
    ON lc_form_field_definition(tenant_id, form_definition_id, sort_order);

-- ========== lc_form_instance ==========
ALTER TABLE lc_form_instance
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS form_definition_version INTEGER,
    ADD COLUMN IF NOT EXISTS schema_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS process_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS process_instance_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_bound_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS workflow_status_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS process_binding_trace_id VARCHAR(128);

UPDATE lc_form_instance instance
SET tenant_id = COALESCE(NULLIF(TRIM(instance.tenant_id), ''), definition.tenant_id, 'GLOBAL'),
    form_definition_version = COALESCE(instance.form_definition_version, definition.version),
    schema_hash = COALESCE(instance.schema_hash, definition.schema_hash),
    created_by = COALESCE(NULLIF(TRIM(instance.created_by), ''), NULLIF(TRIM(instance.submitted_by), ''), 'SYSTEM'),
    updated_by = COALESCE(NULLIF(TRIM(instance.updated_by), ''), NULLIF(TRIM(instance.submitted_by), ''), 'SYSTEM'),
    updated_at = COALESCE(instance.updated_at, instance.submitted_at, instance.created_at, CURRENT_TIMESTAMP),
    workflow_status = COALESCE(instance.workflow_status,
        CASE WHEN instance.process_instance_id IS NULL THEN NULL ELSE 'RUNNING' END),
    workflow_bound_at = CASE
        WHEN instance.process_instance_id IS NULL THEN instance.workflow_bound_at
        ELSE COALESCE(instance.workflow_bound_at, instance.updated_at, instance.submitted_at, CURRENT_TIMESTAMP)
    END,
    workflow_status_updated_at = CASE
        WHEN instance.workflow_status IS NULL THEN instance.workflow_status_updated_at
        ELSE COALESCE(instance.workflow_status_updated_at, instance.updated_at, instance.submitted_at, CURRENT_TIMESTAMP)
    END
FROM lc_form_definition definition
WHERE instance.form_definition_id = definition.id;

UPDATE lc_form_instance
SET tenant_id = COALESCE(NULLIF(TRIM(tenant_id), ''), 'GLOBAL'),
    form_definition_version = COALESCE(form_definition_version, 1),
    schema_hash = COALESCE(schema_hash, md5(COALESCE(data_json, ''))),
    created_by = COALESCE(NULLIF(TRIM(created_by), ''), NULLIF(TRIM(submitted_by), ''), 'SYSTEM'),
    updated_by = COALESCE(NULLIF(TRIM(updated_by), ''), NULLIF(TRIM(submitted_by), ''), 'SYSTEM'),
    updated_at = COALESCE(updated_at, submitted_at, created_at, CURRENT_TIMESTAMP);

DROP INDEX IF EXISTS uk_lc_form_instance_process;

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_instance_tenant_process
    ON lc_form_instance(tenant_id, process_instance_id)
    WHERE process_instance_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_tenant_key_submitted
    ON lc_form_instance(tenant_id, form_key, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_tenant_workflow
    ON lc_form_instance(tenant_id, workflow_status, submitted_at DESC);
