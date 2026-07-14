CREATE TABLE IF NOT EXISTS data_dataset (
    id             VARCHAR(32) PRIMARY KEY,
    dataset_key    VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    dataset_type   VARCHAR(32) NOT NULL,
    owner_id       VARCHAR(64),
    owner_name     VARCHAR(128),
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    backing_table  VARCHAR(128),
    description    VARCHAR(512),
    created_by     VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dataset_key ON data_dataset(dataset_key);
CREATE INDEX IF NOT EXISTS idx_data_dataset_status ON data_dataset(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS data_dataset_field (
    id          VARCHAR(32) PRIMARY KEY,
    dataset_id  VARCHAR(32) NOT NULL REFERENCES data_dataset(id) ON DELETE CASCADE,
    field_key   VARCHAR(128) NOT NULL,
    label       VARCHAR(255) NOT NULL,
    field_type  VARCHAR(64) NOT NULL,
    searchable  SMALLINT NOT NULL DEFAULT 0,
    sortable    SMALLINT NOT NULL DEFAULT 0,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dataset_field_key ON data_dataset_field(dataset_id, field_key);
CREATE INDEX IF NOT EXISTS idx_data_dataset_field_dataset ON data_dataset_field(dataset_id, sort_order);

CREATE TABLE IF NOT EXISTS data_document (
    id             VARCHAR(32) PRIMARY KEY,
    dataset_id     VARCHAR(32) REFERENCES data_dataset(id) ON DELETE SET NULL,
    collection_key VARCHAR(128) NOT NULL,
    source_key     VARCHAR(256),
    title          VARCHAR(255) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    chunk_count    INTEGER NOT NULL DEFAULT 0,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_document_source ON data_document(collection_key, source_key) WHERE source_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_data_document_collection ON data_document(collection_key, updated_at DESC);

CREATE TABLE IF NOT EXISTS data_document_chunk (
    id              VARCHAR(32) PRIMARY KEY,
    document_id     VARCHAR(32) NOT NULL REFERENCES data_document(id) ON DELETE CASCADE,
    collection_key  VARCHAR(128) NOT NULL,
    chunk_index     INTEGER NOT NULL,
    content         TEXT NOT NULL,
    embedding_json  TEXT NOT NULL,
    token_count     INTEGER NOT NULL DEFAULT 0,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_document_chunk_index ON data_document_chunk(document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_data_document_chunk_collection ON data_document_chunk(collection_key);

CREATE TABLE IF NOT EXISTS data_query_audit (
    id                VARCHAR(32) PRIMARY KEY,
    query_mode        VARCHAR(32) NOT NULL,
    dataset_key       VARCHAR(128),
    operator_id       VARCHAR(64),
    operator_name     VARCHAR(128),
    elapsed_ms        BIGINT NOT NULL DEFAULT 0,
    structured_count  INTEGER NOT NULL DEFAULT 0,
    semantic_count    INTEGER NOT NULL DEFAULT 0,
    created_by        VARCHAR(64),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64),
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_data_query_audit_created ON data_query_audit(created_at DESC);

CREATE TABLE IF NOT EXISTS data_sample_expense (
    id             VARCHAR(32) PRIMARY KEY,
    applicant      VARCHAR(128) NOT NULL,
    department     VARCHAR(128) NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    reason         VARCHAR(512),
    status         VARCHAR(32) NOT NULL,
    submitted_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO data_dataset(
    id, dataset_key, name, dataset_type, owner_id, owner_name, status, backing_table,
    description, created_by, updated_by
) VALUES
    ('DSET_EXPENSE_SAMPLE', 'expense_report_sample', 'Expense Report Sample',
     'STRUCTURED', 'SYSTEM', 'System', 'ACTIVE', 'data_sample_expense',
     'Small fixture dataset for hybrid query validation', 'SYSTEM', 'SYSTEM')
ON CONFLICT (dataset_key) DO UPDATE SET
    name = EXCLUDED.name,
    dataset_type = EXCLUDED.dataset_type,
    status = EXCLUDED.status,
    backing_table = EXCLUDED.backing_table,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO data_dataset_field(
    id, dataset_id, field_key, label, field_type, searchable, sortable, sort_order,
    created_by, updated_by
) VALUES
    ('DSET_EXPENSE_FIELD_APPLICANT', 'DSET_EXPENSE_SAMPLE', 'applicant', 'Applicant', 'STRING', 1, 1, 10, 'SYSTEM', 'SYSTEM'),
    ('DSET_EXPENSE_FIELD_DEPARTMENT', 'DSET_EXPENSE_SAMPLE', 'department', 'Department', 'STRING', 1, 1, 20, 'SYSTEM', 'SYSTEM'),
    ('DSET_EXPENSE_FIELD_AMOUNT', 'DSET_EXPENSE_SAMPLE', 'amount', 'Amount', 'NUMBER', 0, 1, 30, 'SYSTEM', 'SYSTEM'),
    ('DSET_EXPENSE_FIELD_STATUS', 'DSET_EXPENSE_SAMPLE', 'status', 'Status', 'STRING', 1, 1, 40, 'SYSTEM', 'SYSTEM'),
    ('DSET_EXPENSE_FIELD_SUBMITTED', 'DSET_EXPENSE_SAMPLE', 'submittedAt', 'Submitted At', 'DATETIME', 0, 1, 50, 'SYSTEM', 'SYSTEM')
ON CONFLICT (dataset_id, field_key) DO UPDATE SET
    label = EXCLUDED.label,
    field_type = EXCLUDED.field_type,
    searchable = EXCLUDED.searchable,
    sortable = EXCLUDED.sortable,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO data_sample_expense(id, applicant, department, amount, reason, status, submitted_at) VALUES
    ('EXP_SAMPLE_001', 'Alice', 'Technology', 1280.50, 'Team workshop materials', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '4 days'),
    ('EXP_SAMPLE_002', 'Bob', 'Finance', 6200.00, 'Annual audit travel', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    ('EXP_SAMPLE_003', 'Carol', 'Technology', 360.00, 'Cloud testing credits', 'APPROVED', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO UPDATE SET
    applicant = EXCLUDED.applicant,
    department = EXCLUDED.department,
    amount = EXCLUDED.amount,
    reason = EXCLUDED.reason,
    status = EXCLUDED.status,
    submitted_at = EXCLUDED.submitted_at;
