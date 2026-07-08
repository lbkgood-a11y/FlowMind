CREATE TABLE IF NOT EXISTS lc_form_definition (
    id             VARCHAR(32)  PRIMARY KEY,
    form_key       VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(512),
    version        INTEGER      NOT NULL DEFAULT 1,
    status         VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    schema_json    TEXT,
    ui_schema_json TEXT,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_definition_key ON lc_form_definition(form_key);

CREATE TABLE IF NOT EXISTS lc_form_field_definition (
    id                 VARCHAR(32)  PRIMARY KEY,
    form_definition_id VARCHAR(32)  NOT NULL REFERENCES lc_form_definition(id) ON DELETE CASCADE,
    field_key          VARCHAR(128) NOT NULL,
    label              VARCHAR(255) NOT NULL,
    field_type         VARCHAR(64)  NOT NULL,
    required_flag      SMALLINT     NOT NULL DEFAULT 0,
    default_value      TEXT,
    placeholder        VARCHAR(255),
    options_json       TEXT,
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_field_key ON lc_form_field_definition(form_definition_id, field_key);

CREATE TABLE IF NOT EXISTS lc_form_instance (
    id                 VARCHAR(32)  PRIMARY KEY,
    form_definition_id VARCHAR(32)  NOT NULL REFERENCES lc_form_definition(id),
    form_key           VARCHAR(128) NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'SUBMITTED',
    data_json          TEXT         NOT NULL,
    submitted_by       VARCHAR(64),
    submitted_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lc_form_instance_key ON lc_form_instance(form_key, submitted_at DESC);
