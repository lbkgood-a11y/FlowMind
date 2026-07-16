CREATE TABLE oa_structure (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    namespace           VARCHAR(128) NOT NULL,
    structure_key       VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    description         VARCHAR(1024),
    structure_kind      VARCHAR(32) NOT NULL,
    data_format         VARCHAR(32) NOT NULL DEFAULT 'JSON',
    direction           VARCHAR(32) NOT NULL DEFAULT 'BIDIRECTIONAL',
    owner_type          VARCHAR(32) NOT NULL,
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_structure_kind CHECK (structure_kind IN ('CANONICAL', 'EXTERNAL', 'TENANT_EXTENSION')),
    CONSTRAINT ck_oa_structure_format CHECK (data_format IN ('JSON')),
    CONSTRAINT ck_oa_structure_direction CHECK (direction IN ('REQUEST', 'RESPONSE', 'BIDIRECTIONAL')),
    CONSTRAINT ck_oa_structure_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_structure_identity
    ON oa_structure (COALESCE(tenant_id, '__PLATFORM__'), namespace, structure_key, structure_kind);
CREATE INDEX idx_oa_structure_tenant_owner ON oa_structure (tenant_id, owner_type, owner_id);

CREATE TABLE oa_structure_version (
    id                          VARCHAR(32) PRIMARY KEY,
    structure_id                VARCHAR(32) NOT NULL REFERENCES oa_structure(id),
    version_number              INTEGER NOT NULL,
    compatibility_line          INTEGER NOT NULL DEFAULT 1,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    schema_content              JSONB NOT NULL,
    schema_hash                 VARCHAR(64) NOT NULL,
    parent_structure_version_id VARCHAR(32) REFERENCES oa_structure_version(id),
    change_summary              VARCHAR(1024),
    semantic_change             JSONB NOT NULL DEFAULT '{}'::jsonb,
    compatibility_result        JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_by                VARCHAR(64),
    published_at                TIMESTAMPTZ,
    deprecated_at               TIMESTAMPTZ,
    archived_at                 TIMESTAMPTZ,
    row_version                 BIGINT NOT NULL DEFAULT 0,
    created_by                  VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64) NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_structure_version UNIQUE (structure_id, version_number),
    CONSTRAINT ck_oa_structure_version_number CHECK (version_number > 0),
    CONSTRAINT ck_oa_structure_compatibility_line CHECK (compatibility_line > 0),
    CONSTRAINT ck_oa_structure_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'))
);

CREATE INDEX idx_oa_structure_version_state ON oa_structure_version (structure_id, lifecycle_state);
CREATE UNIQUE INDEX uk_oa_structure_single_draft
    ON oa_structure_version (structure_id) WHERE lifecycle_state = 'DRAFT';

CREATE TABLE oa_structure_field (
    id                    VARCHAR(32) PRIMARY KEY,
    structure_version_id  VARCHAR(32) NOT NULL REFERENCES oa_structure_version(id) ON DELETE CASCADE,
    json_pointer          VARCHAR(1024) NOT NULL,
    field_name            VARCHAR(256) NOT NULL,
    data_type             VARCHAR(64) NOT NULL,
    required_field        BOOLEAN NOT NULL DEFAULT FALSE,
    array_field           BOOLEAN NOT NULL DEFAULT FALSE,
    semantic_id           VARCHAR(256),
    sensitivity_level     VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    field_constraints     JSONB NOT NULL DEFAULT '{}'::jsonb,
    ordinal               INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_structure_field_path UNIQUE (structure_version_id, json_pointer),
    CONSTRAINT ck_oa_structure_field_sensitivity CHECK (sensitivity_level IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED'))
);

CREATE INDEX idx_oa_structure_field_semantic ON oa_structure_field (semantic_id) WHERE semantic_id IS NOT NULL;

CREATE TABLE oa_structure_provenance (
    id                    VARCHAR(32) PRIMARY KEY,
    structure_version_id  VARCHAR(32) NOT NULL REFERENCES oa_structure_version(id) ON DELETE CASCADE,
    source_type           VARCHAR(32) NOT NULL,
    source_name           VARCHAR(512),
    source_location       VARCHAR(1024),
    document_hash         VARCHAR(64),
    imported_operation    VARCHAR(512),
    metadata              JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by            VARCHAR(64) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_structure_provenance_type CHECK (source_type IN ('MANUAL', 'OPENAPI_IMPORT', 'DERIVED'))
);

CREATE INDEX idx_oa_structure_provenance_version ON oa_structure_provenance (structure_version_id);
