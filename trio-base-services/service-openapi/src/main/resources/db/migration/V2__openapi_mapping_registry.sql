CREATE TABLE oa_mapping_set (
    id                          VARCHAR(32) PRIMARY KEY,
    tenant_id                   VARCHAR(32),
    mapping_key                 VARCHAR(128) NOT NULL,
    display_name                VARCHAR(256) NOT NULL,
    description                 VARCHAR(1024),
    direction                   VARCHAR(32) NOT NULL,
    canonical_structure_id      VARCHAR(32) NOT NULL REFERENCES oa_structure(id),
    external_structure_id       VARCHAR(32) NOT NULL REFERENCES oa_structure(id),
    owner_id                    VARCHAR(64) NOT NULL,
    lifecycle_state             VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version                 BIGINT NOT NULL DEFAULT 0,
    created_by                  VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64) NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_mapping_set_direction CHECK (direction IN ('CANONICAL_TO_EXTERNAL', 'EXTERNAL_TO_CANONICAL')),
    CONSTRAINT ck_oa_mapping_set_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_mapping_set_key
    ON oa_mapping_set (COALESCE(tenant_id, '__PLATFORM__'), mapping_key);

CREATE TABLE oa_mapping_version (
    id                              VARCHAR(32) PRIMARY KEY,
    mapping_set_id                  VARCHAR(32) NOT NULL REFERENCES oa_mapping_set(id),
    version_number                  INTEGER NOT NULL,
    source_structure_version_id     VARCHAR(32) NOT NULL REFERENCES oa_structure_version(id),
    target_structure_version_id     VARCHAR(32) NOT NULL REFERENCES oa_structure_version(id),
    lifecycle_state                 VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    coverage_result                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    compiled_plan                   JSONB,
    compiled_plan_hash              VARCHAR(64),
    published_by                    VARCHAR(64),
    published_at                    TIMESTAMPTZ,
    row_version                     BIGINT NOT NULL DEFAULT 0,
    created_by                      VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                      VARCHAR(64) NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_mapping_version UNIQUE (mapping_set_id, version_number),
    CONSTRAINT ck_oa_mapping_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_mapping_single_draft
    ON oa_mapping_version (mapping_set_id) WHERE lifecycle_state = 'DRAFT';

CREATE TABLE oa_mapping_rule (
    id                      VARCHAR(32) PRIMARY KEY,
    mapping_version_id      VARCHAR(32) NOT NULL REFERENCES oa_mapping_version(id) ON DELETE CASCADE,
    rule_order              INTEGER NOT NULL,
    operation_type          VARCHAR(32) NOT NULL,
    source_pointer          VARCHAR(1024),
    target_pointer          VARCHAR(1024) NOT NULL,
    operation_config        JSONB NOT NULL DEFAULT '{}'::jsonb,
    required_rule           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(64) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_mapping_rule_order UNIQUE (mapping_version_id, rule_order),
    CONSTRAINT ck_oa_mapping_rule_operation CHECK (operation_type IN ('COPY', 'MOVE', 'CONSTANT', 'DEFAULT', 'TYPE_CONVERT', 'CONCATENATE', 'DATE_FORMAT', 'COLLECTION_PROJECT', 'VALUE_MAP'))
);

CREATE INDEX idx_oa_mapping_rule_target ON oa_mapping_rule (mapping_version_id, target_pointer);

CREATE TABLE oa_value_map_set (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32),
    value_map_key       VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    description         VARCHAR(1024),
    owner_id            VARCHAR(64) NOT NULL,
    lifecycle_state     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_value_map_set_state CHECK (lifecycle_state IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_oa_value_map_set_key
    ON oa_value_map_set (COALESCE(tenant_id, '__PLATFORM__'), value_map_key);

CREATE TABLE oa_value_map_version (
    id                      VARCHAR(32) PRIMARY KEY,
    value_map_set_id        VARCHAR(32) NOT NULL REFERENCES oa_value_map_set(id),
    version_number          INTEGER NOT NULL,
    lifecycle_state         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    case_sensitive          BOOLEAN NOT NULL DEFAULT TRUE,
    unmapped_policy         VARCHAR(32) NOT NULL DEFAULT 'FAIL',
    default_canonical_value VARCHAR(1024),
    default_external_value  VARCHAR(1024),
    published_by            VARCHAR(64),
    published_at            TIMESTAMPTZ,
    row_version             BIGINT NOT NULL DEFAULT 0,
    created_by              VARCHAR(64) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_value_map_version UNIQUE (value_map_set_id, version_number),
    CONSTRAINT ck_oa_value_map_version_state CHECK (lifecycle_state IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT ck_oa_value_map_policy CHECK (unmapped_policy IN ('FAIL', 'PASS_THROUGH', 'USE_DEFAULT'))
);

CREATE UNIQUE INDEX uk_oa_value_map_single_draft
    ON oa_value_map_version (value_map_set_id) WHERE lifecycle_state = 'DRAFT';

CREATE TABLE oa_value_map_entry (
    id                    VARCHAR(32) PRIMARY KEY,
    value_map_version_id  VARCHAR(32) NOT NULL REFERENCES oa_value_map_version(id) ON DELETE CASCADE,
    canonical_value       VARCHAR(1024) NOT NULL,
    external_value        VARCHAR(1024) NOT NULL,
    entry_order           INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_value_map_canonical UNIQUE (value_map_version_id, canonical_value),
    CONSTRAINT uk_oa_value_map_external UNIQUE (value_map_version_id, external_value)
);

CREATE TABLE oa_mapping_contract_test (
    id                  VARCHAR(32) PRIMARY KEY,
    mapping_version_id  VARCHAR(32) NOT NULL REFERENCES oa_mapping_version(id) ON DELETE CASCADE,
    test_name           VARCHAR(256) NOT NULL,
    input_payload       JSONB NOT NULL,
    expected_output     JSONB,
    expected_error_code VARCHAR(128),
    required_test       BOOLEAN NOT NULL DEFAULT TRUE,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_mapping_contract_test_name UNIQUE (mapping_version_id, test_name),
    CONSTRAINT ck_oa_mapping_contract_expectation CHECK (expected_output IS NOT NULL OR expected_error_code IS NOT NULL)
);
