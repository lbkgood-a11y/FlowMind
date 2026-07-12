-- Operations foundation: announcements, messages, files, import/export tasks, and jobs.

CREATE TABLE IF NOT EXISTS ops_announcement (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_id       VARCHAR(32) NOT NULL DEFAULT 'default',
    title           VARCHAR(160) NOT NULL,
    content         TEXT NOT NULL,
    priority        VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    target_type     VARCHAR(16) NOT NULL DEFAULT 'ALL',
    target_org_ids  TEXT,
    target_user_ids TEXT,
    publish_at      TIMESTAMP,
    unpublish_at    TIMESTAMP,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_announcement_status
    ON ops_announcement(tenant_id, status, publish_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_announcement_priority
    ON ops_announcement(tenant_id, priority, updated_at DESC);

CREATE TABLE IF NOT EXISTS ops_announcement_read (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_id       VARCHAR(32) NOT NULL DEFAULT 'default',
    announcement_id VARCHAR(32) NOT NULL REFERENCES ops_announcement(id) ON DELETE CASCADE,
    user_id         VARCHAR(32) NOT NULL,
    read_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ops_announcement_read_user
    ON ops_announcement_read(announcement_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ops_announcement_read_user
    ON ops_announcement_read(tenant_id, user_id, read_at DESC);

CREATE TABLE IF NOT EXISTS ops_message (
    id           VARCHAR(32) PRIMARY KEY,
    tenant_id    VARCHAR(32) NOT NULL DEFAULT 'default',
    title        VARCHAR(160) NOT NULL,
    content      TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    source_type  VARCHAR(32),
    source_id    VARCHAR(32),
    sender_id    VARCHAR(32),
    sender_name  VARCHAR(64),
    created_by   VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(32),
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_message_type
    ON ops_message(tenant_id, message_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_message_source
    ON ops_message(tenant_id, source_type, source_id);

CREATE TABLE IF NOT EXISTS ops_message_recipient (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL DEFAULT 'default',
    message_id        VARCHAR(32) NOT NULL REFERENCES ops_message(id) ON DELETE CASCADE,
    recipient_user_id VARCHAR(32) NOT NULL,
    read_status       SMALLINT NOT NULL DEFAULT 0,
    read_at           TIMESTAMP,
    deleted_at        TIMESTAMP,
    created_by        VARCHAR(32),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(32),
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ops_message_recipient
    ON ops_message_recipient(message_id, recipient_user_id);
CREATE INDEX IF NOT EXISTS idx_ops_message_recipient_user
    ON ops_message_recipient(tenant_id, recipient_user_id, read_status, created_at DESC);

CREATE TABLE IF NOT EXISTS ops_file (
    id               VARCHAR(32) PRIMARY KEY,
    tenant_id        VARCHAR(32) NOT NULL DEFAULT 'default',
    original_name    VARCHAR(256) NOT NULL,
    storage_name     VARCHAR(128) NOT NULL,
    content_type     VARCHAR(128),
    extension        VARCHAR(32),
    file_size        BIGINT NOT NULL,
    storage_path     VARCHAR(512) NOT NULL,
    checksum         VARCHAR(128),
    owner_user_id    VARCHAR(32),
    status           SMALLINT NOT NULL DEFAULT 1,
    deleted          SMALLINT NOT NULL DEFAULT 0,
    download_count   BIGINT NOT NULL DEFAULT 0,
    last_download_at TIMESTAMP,
    created_by       VARCHAR(32),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(32),
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_file_owner
    ON ops_file(tenant_id, owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_file_name
    ON ops_file(tenant_id, original_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_file_status
    ON ops_file(tenant_id, status, deleted, created_at DESC);

CREATE TABLE IF NOT EXISTS ops_file_reference (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL DEFAULT 'default',
    file_id       VARCHAR(32) NOT NULL REFERENCES ops_file(id) ON DELETE CASCADE,
    business_type VARCHAR(64) NOT NULL,
    business_id   VARCHAR(64) NOT NULL,
    ref_type      VARCHAR(32) NOT NULL DEFAULT 'ATTACHMENT',
    created_by    VARCHAR(32),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ops_file_reference
    ON ops_file_reference(tenant_id, file_id, business_type, business_id, ref_type);
CREATE INDEX IF NOT EXISTS idx_ops_file_reference_business
    ON ops_file_reference(tenant_id, business_type, business_id);

CREATE TABLE IF NOT EXISTS ops_import_export_task (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_id       VARCHAR(32) NOT NULL DEFAULT 'default',
    task_type       VARCHAR(16) NOT NULL,
    business_type   VARCHAR(64) NOT NULL,
    task_name       VARCHAR(160) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress        INTEGER NOT NULL DEFAULT 0,
    request_params  TEXT,
    result_file_id  VARCHAR(32) REFERENCES ops_file(id),
    failure_file_id VARCHAR(32) REFERENCES ops_file(id),
    success_count   INTEGER NOT NULL DEFAULT 0,
    failure_count   INTEGER NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(512),
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_import_export_task_creator
    ON ops_import_export_task(tenant_id, created_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_import_export_task_status
    ON ops_import_export_task(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_import_export_task_business
    ON ops_import_export_task(tenant_id, business_type, task_type, created_at DESC);

CREATE TABLE IF NOT EXISTS ops_job_definition (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_id       VARCHAR(32) NOT NULL DEFAULT 'default',
    job_code        VARCHAR(64) NOT NULL,
    job_name        VARCHAR(128) NOT NULL,
    handler_name    VARCHAR(128) NOT NULL,
    cron_expression VARCHAR(64) NOT NULL,
    job_params      TEXT,
    enabled         SMALLINT NOT NULL DEFAULT 0,
    description     VARCHAR(256),
    last_run_at     TIMESTAMP,
    next_run_at     TIMESTAMP,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ops_job_definition_code
    ON ops_job_definition(tenant_id, job_code);
CREATE INDEX IF NOT EXISTS idx_ops_job_definition_enabled
    ON ops_job_definition(tenant_id, enabled, updated_at DESC);

CREATE TABLE IF NOT EXISTS ops_job_execution_log (
    id             VARCHAR(32) PRIMARY KEY,
    tenant_id      VARCHAR(32) NOT NULL DEFAULT 'default',
    job_id         VARCHAR(32) NOT NULL REFERENCES ops_job_definition(id) ON DELETE CASCADE,
    job_code       VARCHAR(64) NOT NULL,
    trigger_type   VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at       TIMESTAMP,
    duration_ms    BIGINT,
    result_summary VARCHAR(512),
    error_message  TEXT,
    run_instance   VARCHAR(128),
    triggered_by   VARCHAR(32),
    created_by     VARCHAR(32),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(32),
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_job_execution_log_job
    ON ops_job_execution_log(tenant_id, job_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_job_execution_log_status
    ON ops_job_execution_log(tenant_id, status, started_at DESC);
