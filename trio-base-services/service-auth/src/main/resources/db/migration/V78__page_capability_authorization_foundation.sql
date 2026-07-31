-- Business-facing page capability authorization foundation.
-- Draft intent is managed independently; only an explicitly published release
-- is projected into runtime grants and policies.

CREATE TABLE IF NOT EXISTS sys_auth_page_catalog (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    catalog_code      VARCHAR(96) NOT NULL,
    catalog_version   BIGINT NOT NULL,
    source_type       VARCHAR(32) NOT NULL,
    source_ref        VARCHAR(160),
    manifest_hash     VARCHAR(128) NOT NULL,
    lifecycle_status  VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    activated_at      TIMESTAMP,
    created_by        VARCHAR(32),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(32),
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_page_catalog_version CHECK (catalog_version > 0),
    CONSTRAINT ck_auth_page_catalog_source CHECK (
        source_type IN ('SYSTEM_MANIFEST', 'OWNER_MANIFEST', 'LOWCODE_PUBLICATION')
    ),
    CONSTRAINT ck_auth_page_catalog_status CHECK (
        lifecycle_status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'REJECTED')
    ),
    CONSTRAINT uk_auth_page_catalog_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_auth_page_catalog_version UNIQUE (tenant_id, catalog_code, catalog_version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_page_catalog_active
    ON sys_auth_page_catalog(tenant_id, catalog_code)
    WHERE lifecycle_status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_auth_page_catalog_status
    ON sys_auth_page_catalog(tenant_id, lifecycle_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_page_capability (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    catalog_id            VARCHAR(32) NOT NULL,
    menu_id               VARCHAR(32),
    page_code             VARCHAR(128) NOT NULL,
    page_name             VARCHAR(160) NOT NULL,
    capability_code       VARCHAR(160) NOT NULL,
    capability_name       VARCHAR(160) NOT NULL,
    capability_category   VARCHAR(24) NOT NULL,
    help_text             VARCHAR(512),
    readiness_status      VARCHAR(24) NOT NULL DEFAULT 'UNMAPPED',
    readiness_message     VARCHAR(512),
    scope_supported       SMALLINT NOT NULL DEFAULT 0,
    field_policy_supported SMALLINT NOT NULL DEFAULT 0,
    sort_order            INTEGER NOT NULL DEFAULT 0,
    status                SMALLINT NOT NULL DEFAULT 1,
    metadata_json         TEXT,
    created_by            VARCHAR(32),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(32),
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_page_capability_category CHECK (
        capability_category IN ('ACCESS', 'READ', 'OPERATION')
    ),
    CONSTRAINT ck_auth_page_capability_readiness CHECK (
        readiness_status IN ('READY', 'PARTIAL', 'BROKEN', 'UNMAPPED')
    ),
    CONSTRAINT ck_auth_page_capability_scope_flag CHECK (scope_supported IN (0, 1)),
    CONSTRAINT ck_auth_page_capability_field_flag CHECK (field_policy_supported IN (0, 1)),
    CONSTRAINT ck_auth_page_capability_status CHECK (status IN (0, 1)),
    CONSTRAINT uk_auth_page_capability_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_auth_page_capability_code UNIQUE (tenant_id, catalog_id, capability_code),
    CONSTRAINT fk_auth_page_capability_catalog FOREIGN KEY (tenant_id, catalog_id)
        REFERENCES sys_auth_page_catalog(tenant_id, id),
    CONSTRAINT fk_auth_page_capability_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_page_capability_page
    ON sys_auth_page_capability(tenant_id, catalog_id, page_code, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_auth_page_capability_readiness
    ON sys_auth_page_capability(tenant_id, readiness_status, status);

CREATE TABLE IF NOT EXISTS sys_auth_page_capability_target (
    id                   VARCHAR(32) PRIMARY KEY,
    tenant_id            VARCHAR(32) NOT NULL,
    capability_id        VARCHAR(32) NOT NULL,
    resource_code        VARCHAR(160) NOT NULL,
    action_code          VARCHAR(64) NOT NULL,
    target_kind          VARCHAR(24) NOT NULL DEFAULT 'GRANT',
    required_flag        SMALLINT NOT NULL DEFAULT 1,
    status               SMALLINT NOT NULL DEFAULT 1,
    created_by           VARCHAR(32),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(32),
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_page_target_kind CHECK (
        target_kind IN ('GRANT', 'DATA_POLICY', 'FIELD_POLICY', 'GUARD')
    ),
    CONSTRAINT ck_auth_page_target_required CHECK (required_flag IN (0, 1)),
    CONSTRAINT ck_auth_page_target_status CHECK (status IN (0, 1)),
    CONSTRAINT uk_auth_page_target_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_auth_page_target_action UNIQUE (
        tenant_id, capability_id, resource_code, action_code, target_kind
    ),
    CONSTRAINT fk_auth_page_target_capability FOREIGN KEY (tenant_id, capability_id)
        REFERENCES sys_auth_page_capability(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_auth_page_target_runtime
    ON sys_auth_page_capability_target(tenant_id, resource_code, action_code, status);

CREATE TABLE IF NOT EXISTS sys_auth_page_capability_dependency (
    id                       VARCHAR(32) PRIMARY KEY,
    tenant_id                VARCHAR(32) NOT NULL,
    capability_id            VARCHAR(32) NOT NULL,
    required_capability_id   VARCHAR(32) NOT NULL,
    created_by               VARCHAR(32),
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_page_dependency_self CHECK (capability_id <> required_capability_id),
    CONSTRAINT uk_auth_page_dependency_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_auth_page_dependency_pair UNIQUE (
        tenant_id, capability_id, required_capability_id
    ),
    CONSTRAINT fk_auth_page_dependency_source FOREIGN KEY (tenant_id, capability_id)
        REFERENCES sys_auth_page_capability(tenant_id, id),
    CONSTRAINT fk_auth_page_dependency_required FOREIGN KEY (tenant_id, required_capability_id)
        REFERENCES sys_auth_page_capability(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_auth_page_dependency_required
    ON sys_auth_page_capability_dependency(tenant_id, required_capability_id);

CREATE TABLE IF NOT EXISTS sys_role_auth_draft (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    role_id               VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    catalog_id            VARCHAR(32) NOT NULL,
    based_release_id      VARCHAR(32),
    draft_status          VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    intent_version        BIGINT NOT NULL DEFAULT 1,
    validation_token_hash VARCHAR(128),
    validation_plan_hash VARCHAR(128),
    validated_by          VARCHAR(32),
    validation_authority_version BIGINT,
    migration_review_required SMALLINT NOT NULL DEFAULT 0,
    migration_expansion_detected SMALLINT NOT NULL DEFAULT 0,
    validated_at          TIMESTAMP,
    validation_expires_at TIMESTAMP,
    validation_summary   TEXT,
    created_by            VARCHAR(32),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(32),
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_auth_draft_status CHECK (
        draft_status IN ('DRAFT', 'VALIDATED', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'ABANDONED')
    ),
    CONSTRAINT ck_role_auth_draft_version CHECK (intent_version > 0),
    CONSTRAINT ck_role_auth_draft_migration_review CHECK (migration_review_required IN (0, 1)),
    CONSTRAINT ck_role_auth_draft_migration_expansion CHECK (migration_expansion_detected IN (0, 1)),
    CONSTRAINT uk_role_auth_draft_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_role_auth_draft_catalog FOREIGN KEY (tenant_id, catalog_id)
        REFERENCES sys_auth_page_catalog(tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_auth_draft_open
    ON sys_role_auth_draft(tenant_id, role_id)
    WHERE draft_status IN ('DRAFT', 'VALIDATED', 'PUBLISHING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_role_auth_draft_catalog
    ON sys_role_auth_draft(tenant_id, catalog_id, draft_status);

CREATE TABLE IF NOT EXISTS sys_role_auth_intent (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    draft_id              VARCHAR(32) NOT NULL,
    capability_id         VARCHAR(32) NOT NULL,
    selection_source      VARCHAR(24) NOT NULL DEFAULT 'EXPLICIT',
    default_scope_type    VARCHAR(48),
    default_scope_ids     TEXT,
    operation_scope_type  VARCHAR(48),
    operation_scope_ids   TEXT,
    field_intent_json     TEXT,
    constraint_intent_json TEXT,
    created_by            VARCHAR(32),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(32),
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_auth_intent_source CHECK (
        selection_source IN ('EXPLICIT', 'DEPENDENCY', 'MIGRATION')
    ),
    CONSTRAINT uk_role_auth_intent_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_role_auth_intent_capability UNIQUE (tenant_id, draft_id, capability_id),
    CONSTRAINT fk_role_auth_intent_draft FOREIGN KEY (tenant_id, draft_id)
        REFERENCES sys_role_auth_draft(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_role_auth_intent_capability FOREIGN KEY (tenant_id, capability_id)
        REFERENCES sys_auth_page_capability(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_role_auth_intent_draft
    ON sys_role_auth_intent(tenant_id, draft_id, selection_source);

CREATE TABLE IF NOT EXISTS sys_role_auth_release (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    role_id               VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    catalog_id            VARCHAR(32) NOT NULL,
    draft_id              VARCHAR(32),
    previous_release_id   VARCHAR(32),
    release_number        BIGINT NOT NULL,
    intent_version        BIGINT NOT NULL,
    catalog_version       BIGINT NOT NULL,
    validation_hash       VARCHAR(128) NOT NULL,
    intent_snapshot       TEXT NOT NULL,
    compiled_snapshot     TEXT NOT NULL,
    business_summary      TEXT NOT NULL,
    published_by          VARCHAR(32) NOT NULL,
    published_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_auth_release_number CHECK (release_number > 0),
    CONSTRAINT ck_role_auth_release_intent_version CHECK (intent_version > 0),
    CONSTRAINT ck_role_auth_release_catalog_version CHECK (catalog_version > 0),
    CONSTRAINT uk_role_auth_release_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_role_auth_release_number UNIQUE (tenant_id, role_id, release_number),
    CONSTRAINT fk_role_auth_release_catalog FOREIGN KEY (tenant_id, catalog_id)
        REFERENCES sys_auth_page_catalog(tenant_id, id),
    CONSTRAINT fk_role_auth_release_draft FOREIGN KEY (tenant_id, draft_id)
        REFERENCES sys_role_auth_draft(tenant_id, id),
    CONSTRAINT fk_role_auth_release_previous FOREIGN KEY (tenant_id, previous_release_id)
        REFERENCES sys_role_auth_release(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_role_auth_release_role
    ON sys_role_auth_release(tenant_id, role_id, published_at DESC);

ALTER TABLE sys_role_auth_draft
    ADD CONSTRAINT fk_role_auth_draft_based_release
    FOREIGN KEY (tenant_id, based_release_id)
    REFERENCES sys_role_auth_release(tenant_id, id);

CREATE TABLE IF NOT EXISTS sys_role_auth_active_release (
    tenant_id       VARCHAR(32) NOT NULL,
    role_id         VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    release_id      VARCHAR(32) NOT NULL,
    activated_by    VARCHAR(32) NOT NULL,
    activated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activation_type VARCHAR(24) NOT NULL DEFAULT 'PUBLISH',
    PRIMARY KEY (tenant_id, role_id),
    CONSTRAINT ck_role_auth_activation_type CHECK (
        activation_type IN ('PUBLISH', 'ROLLBACK')
    ),
    CONSTRAINT fk_role_auth_active_release FOREIGN KEY (tenant_id, release_id)
        REFERENCES sys_role_auth_release(tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_auth_active_release_id
    ON sys_role_auth_active_release(tenant_id, release_id);

CREATE TABLE IF NOT EXISTS sys_role_auth_compiled_evidence (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    release_id            VARCHAR(32) NOT NULL,
    capability_code       VARCHAR(160) NOT NULL,
    projection_type       VARCHAR(24) NOT NULL,
    projection_key        VARCHAR(320) NOT NULL,
    resource_code         VARCHAR(160),
    action_code           VARCHAR(64),
    effect                VARCHAR(16),
    projection_snapshot   TEXT NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_auth_projection_type CHECK (
        projection_type IN ('GRANT', 'DATA_POLICY', 'FIELD_POLICY', 'GUARD')
    ),
    CONSTRAINT ck_role_auth_projection_effect CHECK (
        effect IS NULL OR effect IN ('ALLOW', 'DENY')
    ),
    CONSTRAINT uk_role_auth_evidence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_role_auth_evidence_projection UNIQUE (
        tenant_id, release_id, capability_code, projection_type, projection_key
    ),
    CONSTRAINT fk_role_auth_evidence_release FOREIGN KEY (tenant_id, release_id)
        REFERENCES sys_role_auth_release(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_role_auth_evidence_runtime
    ON sys_role_auth_compiled_evidence(tenant_id, resource_code, action_code, projection_type);

CREATE TABLE IF NOT EXISTS sys_role_auth_drift (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    role_id               VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    release_id            VARCHAR(32) NOT NULL,
    capability_code       VARCHAR(160),
    old_catalog_version   BIGINT NOT NULL,
    new_catalog_version   BIGINT NOT NULL,
    drift_type            VARCHAR(32) NOT NULL,
    drift_status          VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    affected_user_count   BIGINT NOT NULL DEFAULT 0,
    impact_summary        TEXT NOT NULL,
    detected_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by           VARCHAR(32),
    resolved_at           TIMESTAMP,
    resolution_release_id VARCHAR(32),
    CONSTRAINT ck_role_auth_drift_versions CHECK (new_catalog_version <> old_catalog_version),
    CONSTRAINT ck_role_auth_drift_type CHECK (
        drift_type IN ('TARGET_ADDED', 'TARGET_REMOVED', 'TARGET_CHANGED', 'CAPABILITY_REMOVED', 'DEPENDENCY_CHANGED', 'ENFORCEMENT_CHANGED')
    ),
    CONSTRAINT ck_role_auth_drift_status CHECK (
        drift_status IN ('OPEN', 'REVIEWED', 'RESOLVED', 'IGNORED')
    ),
    CONSTRAINT ck_role_auth_drift_users CHECK (affected_user_count >= 0),
    CONSTRAINT uk_role_auth_drift_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_role_auth_drift_release FOREIGN KEY (tenant_id, release_id)
        REFERENCES sys_role_auth_release(tenant_id, id),
    CONSTRAINT fk_role_auth_drift_resolution FOREIGN KEY (tenant_id, resolution_release_id)
        REFERENCES sys_role_auth_release(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_role_auth_drift_open
    ON sys_role_auth_drift(tenant_id, drift_status, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_role_auth_drift_role
    ON sys_role_auth_drift(tenant_id, role_id, detected_at DESC);

CREATE TABLE IF NOT EXISTS sys_role_auth_audit (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL,
    role_id            VARCHAR(32),
    draft_id           VARCHAR(32),
    release_id         VARCHAR(32),
    event_type         VARCHAR(32) NOT NULL,
    actor_id           VARCHAR(32) NOT NULL,
    business_summary   TEXT NOT NULL,
    technical_evidence TEXT,
    affected_user_count BIGINT NOT NULL DEFAULT 0,
    trace_id           VARCHAR(128),
    occurred_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_auth_audit_event CHECK (
        event_type IN ('DRAFT_CREATED', 'DRAFT_CHANGED', 'DEPENDENCY_RESOLVED', 'VALIDATED', 'VALIDATION_FAILED', 'PUBLISHED', 'PUBLISH_FAILED', 'ROLLED_BACK', 'DRIFT_DETECTED', 'MIGRATION_ANALYZED')
    ),
    CONSTRAINT ck_role_auth_audit_users CHECK (affected_user_count >= 0),
    CONSTRAINT fk_role_auth_audit_draft FOREIGN KEY (tenant_id, draft_id)
        REFERENCES sys_role_auth_draft(tenant_id, id),
    CONSTRAINT fk_role_auth_audit_release FOREIGN KEY (tenant_id, release_id)
        REFERENCES sys_role_auth_release(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_role_auth_audit_role
    ON sys_role_auth_audit(tenant_id, role_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_role_auth_audit_event
    ON sys_role_auth_audit(tenant_id, event_type, occurred_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_tenant_management_mode (
    tenant_id       VARCHAR(32) PRIMARY KEY,
    management_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY',
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_tenant_management_mode CHECK (
        management_mode IN ('LEGACY', 'MIGRATION', 'PAGE_CAPABILITY')
    )
);

INSERT INTO sys_auth_tenant_management_mode(tenant_id, management_mode, updated_by)
VALUES ('default', 'MIGRATION', 'SYSTEM')
ON CONFLICT (tenant_id) DO NOTHING;

CREATE OR REPLACE FUNCTION prevent_role_auth_release_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Published role authorization releases are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_role_auth_release_immutable ON sys_role_auth_release;
CREATE TRIGGER trg_role_auth_release_immutable
    BEFORE UPDATE OR DELETE ON sys_role_auth_release
    FOR EACH ROW EXECUTE FUNCTION prevent_role_auth_release_mutation();

CREATE OR REPLACE FUNCTION prevent_role_auth_evidence_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Compiled role authorization evidence is immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_role_auth_evidence_immutable ON sys_role_auth_compiled_evidence;
CREATE TRIGGER trg_role_auth_evidence_immutable
    BEFORE UPDATE OR DELETE ON sys_role_auth_compiled_evidence
    FOR EACH ROW EXECUTE FUNCTION prevent_role_auth_evidence_mutation();

INSERT INTO sys_auth_version(version_key, version_value) VALUES
    ('PAGE_CAPABILITY_CATALOG', 1),
    ('ROLE_AUTHORIZATION_RELEASE', 1)
ON CONFLICT (version_key) DO NOTHING;
