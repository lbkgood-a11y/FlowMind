-- Versioned application form relations and persisted instance graphs.

CREATE TABLE IF NOT EXISTS lc_form_relation (
    id                         VARCHAR(32)  PRIMARY KEY,
    tenant_id                  VARCHAR(32)  NOT NULL DEFAULT 'default',
    application_version_id     VARCHAR(32)  NOT NULL REFERENCES lc_application_version(id) ON DELETE CASCADE,
    relation_code              VARCHAR(128) NOT NULL,
    parent_form_definition_id  VARCHAR(32)  NOT NULL REFERENCES lc_form_definition(id),
    child_form_definition_id   VARCHAR(32)  NOT NULL REFERENCES lc_form_definition(id),
    cardinality                VARCHAR(16)  NOT NULL DEFAULT 'MANY',
    parent_key_field           VARCHAR(128) NOT NULL DEFAULT 'id',
    child_foreign_key_field    VARCHAR(128) NOT NULL,
    cascade_save               SMALLINT     NOT NULL DEFAULT 1,
    cascade_delete             SMALLINT     NOT NULL DEFAULT 0,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    created_by                 VARCHAR(64),
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64),
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_lc_form_relation_cardinality CHECK (cardinality IN ('ONE', 'MANY')),
    CONSTRAINT ck_lc_form_relation_not_self CHECK (parent_form_definition_id <> child_form_definition_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_relation_version_code
    ON lc_form_relation(application_version_id, relation_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_relation_version_pair
    ON lc_form_relation(application_version_id, parent_form_definition_id, child_form_definition_id);
CREATE INDEX IF NOT EXISTS idx_lc_form_relation_tenant_version
    ON lc_form_relation(tenant_id, application_version_id, sort_order);

CREATE TABLE IF NOT EXISTS lc_form_instance_relation (
    id                         VARCHAR(32)  PRIMARY KEY,
    tenant_id                  VARCHAR(32)  NOT NULL DEFAULT 'default',
    application_version_id     VARCHAR(32)  NOT NULL REFERENCES lc_application_version(id),
    relation_code              VARCHAR(128) NOT NULL,
    parent_instance_id         VARCHAR(32)  NOT NULL REFERENCES lc_form_instance(id) ON DELETE CASCADE,
    child_instance_id          VARCHAR(32)  NOT NULL REFERENCES lc_form_instance(id) ON DELETE CASCADE,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    created_by                 VARCHAR(64),
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64),
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_instance_relation_child
    ON lc_form_instance_relation(application_version_id, relation_code, child_instance_id);
CREATE INDEX IF NOT EXISTS idx_lc_form_instance_relation_parent
    ON lc_form_instance_relation(tenant_id, parent_instance_id, relation_code, sort_order);
