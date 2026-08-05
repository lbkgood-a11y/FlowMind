-- 目的：为版本化公告、幂等通知投递、个人收件投影和渠道配置增加纯增量存储。
-- 不变量：所有运行时唯一约束都包含 tenant_id；证据表采用追加写，禁止覆盖已发布正文或回执。
-- 回滚：应用切回 legacy feature flag 后可停止写入这些表；兼容窗口内不得删除旧表或执行反向 DDL。

CREATE TABLE IF NOT EXISTS ops_announcement_identity (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL,
    announcement_code  VARCHAR(64) NOT NULL,
    current_version_id VARCHAR(32),
    legacy_id          VARCHAR(32),
    created_by         VARCHAR(32),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(32),
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, announcement_code),
    UNIQUE (tenant_id, legacy_id)
);

CREATE TABLE IF NOT EXISTS ops_announcement_version (
    id                             VARCHAR(32) PRIMARY KEY,
    tenant_id                      VARCHAR(32) NOT NULL,
    announcement_id                VARCHAR(32) NOT NULL REFERENCES ops_announcement_identity(id),
    version_no                     INTEGER NOT NULL,
    title                          VARCHAR(160) NOT NULL,
    content                        TEXT NOT NULL,
    priority                       VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    lifecycle_state                VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    audience_mode                  VARCHAR(16) NOT NULL DEFAULT 'DYNAMIC',
    confirmation_required          SMALLINT NOT NULL DEFAULT 0,
    confirmation_statement         VARCHAR(512),
    confirmation_statement_hash    VARCHAR(128),
    confirmation_deadline          TIMESTAMP,
    scheduled_publish_at           TIMESTAMP,
    published_at                   TIMESTAMP,
    effective_until                TIMESTAMP,
    pin_from                       TIMESTAMP,
    pin_until                      TIMESTAMP,
    predecessor_version_id         VARCHAR(32),
    withdrawal_reason              VARCHAR(512),
    withdrawn_at                   TIMESTAMP,
    created_by                     VARCHAR(32),
    created_at                     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                     VARCHAR(32),
    updated_at                     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, announcement_id, version_no),
    CHECK (lifecycle_state IN ('DRAFT','PENDING_REVIEW','REJECTED','SCHEDULED','PUBLISHED','EXPIRED','WITHDRAWN','SUPERSEDED')),
    CHECK (audience_mode IN ('DYNAMIC','FROZEN'))
);

ALTER TABLE ops_announcement_identity
    ADD CONSTRAINT fk_ops_announcement_current_version
    FOREIGN KEY (current_version_id) REFERENCES ops_announcement_version(id);

CREATE TABLE IF NOT EXISTS ops_announcement_transition (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    version_id        VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    from_state        VARCHAR(24),
    to_state          VARCHAR(24) NOT NULL,
    transition_type   VARCHAR(32) NOT NULL,
    reason            VARCHAR(512),
    actor_id          VARCHAR(32),
    actor_name        VARCHAR(64),
    trace_id          VARCHAR(128),
    occurred_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ops_announcement_audience_rule (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    version_id          VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    selector_type       VARCHAR(32) NOT NULL,
    subject_id          VARCHAR(64),
    include_descendants SMALLINT NOT NULL DEFAULT 0,
    resolver_key        VARCHAR(128),
    resolver_version    VARCHAR(32),
    created_by          VARCHAR(32),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (selector_type IN ('ALL','ORGANIZATION','ROLE','USER','DYNAMIC_PARTICIPANT'))
);

CREATE TABLE IF NOT EXISTS ops_announcement_recipient_snapshot (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    version_id        VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    recipient_user_id VARCHAR(32) NOT NULL,
    resolver_key      VARCHAR(128),
    resolver_version  VARCHAR(32),
    resolved_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, version_id, recipient_user_id)
);

CREATE TABLE IF NOT EXISTS ops_announcement_receipt (
    id                          VARCHAR(32) PRIMARY KEY,
    tenant_id                   VARCHAR(32) NOT NULL,
    version_id                  VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    recipient_user_id           VARCHAR(32) NOT NULL,
    read_at                     TIMESTAMP,
    confirmed_at                TIMESTAMP,
    confirmation_statement_hash VARCHAR(128),
    confirmation_trace_id       VARCHAR(128),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, version_id, recipient_user_id)
);

CREATE TABLE IF NOT EXISTS ops_announcement_reminder (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    version_id            VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    recipient_user_id     VARCHAR(32) NOT NULL,
    reminder_key          VARCHAR(128) NOT NULL,
    notification_task_id  VARCHAR(32),
    requested_by          VARCHAR(32),
    trace_id              VARCHAR(128),
    requested_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, reminder_key, recipient_user_id)
);

CREATE TABLE IF NOT EXISTS ops_notification_task (
    id                   VARCHAR(32) PRIMARY KEY,
    tenant_id            VARCHAR(32) NOT NULL,
    producer             VARCHAR(64) NOT NULL,
    event_id             VARCHAR(128) NOT NULL,
    idempotency_key      VARCHAR(160) NOT NULL,
    schema_version       VARCHAR(16) NOT NULL,
    template_key         VARCHAR(128) NOT NULL,
    template_version     VARCHAR(32),
    request_payload      TEXT NOT NULL,
    task_state           VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    audience_mode        VARCHAR(16) NOT NULL,
    resolved_count       BIGINT NOT NULL DEFAULT 0,
    delivered_count      BIGINT NOT NULL DEFAULT 0,
    failed_count         BIGINT NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMP,
    expires_at           TIMESTAMP,
    cancellation_reason  VARCHAR(512),
    trace_id             VARCHAR(128),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, producer, idempotency_key),
    CHECK (task_state IN ('ACCEPTED','RESOLVING','DELIVERING','PARTIALLY_DELIVERED','DELIVERED','FAILED','EXPIRED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS ops_notification_resolution_checkpoint (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    task_id           VARCHAR(32) NOT NULL REFERENCES ops_notification_task(id),
    resolver_key      VARCHAR(128) NOT NULL,
    resolver_version  VARCHAR(32) NOT NULL,
    cursor_value      VARCHAR(256),
    resolution_state  VARCHAR(24) NOT NULL,
    resolved_count    BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, task_id, resolver_key)
);

CREATE TABLE IF NOT EXISTS ops_inbox_projection (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    task_id               VARCHAR(32) NOT NULL REFERENCES ops_notification_task(id),
    recipient_user_id     VARCHAR(32) NOT NULL,
    channel_code          VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    item_type             VARCHAR(32) NOT NULL,
    title                 VARCHAR(160) NOT NULL,
    summary               VARCHAR(512),
    source_owner          VARCHAR(64),
    resource_type         VARCHAR(64),
    resource_id           VARCHAR(64),
    resource_key          VARCHAR(128),
    action_id             VARCHAR(128),
    read_at               TIMESTAMP,
    archived_at           TIMESTAMP,
    hidden_at             TIMESTAMP,
    withdrawn_at          TIMESTAMP,
    received_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, task_id, recipient_user_id, channel_code)
);

CREATE TABLE IF NOT EXISTS ops_notification_delivery_attempt (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL,
    task_id            VARCHAR(32) NOT NULL REFERENCES ops_notification_task(id),
    projection_id      VARCHAR(32),
    recipient_user_id  VARCHAR(32) NOT NULL,
    channel_code       VARCHAR(16) NOT NULL,
    attempt_no         INTEGER NOT NULL,
    delivery_status    VARCHAR(32) NOT NULL,
    retryable          SMALLINT NOT NULL DEFAULT 0,
    error_category     VARCHAR(64),
    sanitized_message  VARCHAR(512),
    occurred_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, task_id, recipient_user_id, channel_code, attempt_no)
);

CREATE TABLE IF NOT EXISTS ops_notification_outbox (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    aggregate_type    VARCHAR(64) NOT NULL,
    aggregate_id      VARCHAR(32) NOT NULL,
    event_type        VARCHAR(96) NOT NULL,
    event_id          VARCHAR(128) NOT NULL,
    payload           TEXT NOT NULL,
    trace_id          VARCHAR(128),
    published_at      TIMESTAMP,
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, event_id)
);

CREATE TABLE IF NOT EXISTS ops_inbox_change_sequence (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    recipient_user_id VARCHAR(32) NOT NULL,
    event_id          VARCHAR(128) NOT NULL,
    event_kind        VARCHAR(32) NOT NULL,
    change_hint       VARCHAR(128) NOT NULL,
    occurred_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at      TIMESTAMP,
    UNIQUE (tenant_id, event_id)
);

CREATE TABLE IF NOT EXISTS ops_notification_channel (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    channel_code      VARCHAR(16) NOT NULL,
    capability_state  VARCHAR(24) NOT NULL,
    desired_enabled   SMALLINT NOT NULL DEFAULT 0,
    adapter_key       VARCHAR(128),
    adapter_version   VARCHAR(32),
    validated_at      TIMESTAMP,
    validation_summary VARCHAR(512),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, channel_code),
    CHECK (channel_code IN ('IN_APP','EMAIL','SMS','WE_COM','DINGTALK')),
    CHECK (capability_state IN ('NOT_CONNECTED','INVALID','READY','DISABLED','DEGRADED'))
);

CREATE TABLE IF NOT EXISTS ops_notification_provider (
    id                   VARCHAR(32) PRIMARY KEY,
    tenant_id            VARCHAR(32) NOT NULL,
    channel_code         VARCHAR(16) NOT NULL,
    provider_key         VARCHAR(64) NOT NULL,
    display_name         VARCHAR(128) NOT NULL,
    credential_reference VARCHAR(256),
    settings_json        TEXT,
    enabled              SMALLINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, channel_code, provider_key)
);

CREATE TABLE IF NOT EXISTS ops_notification_template (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL,
    template_key       VARCHAR(128) NOT NULL,
    channel_code       VARCHAR(16) NOT NULL,
    locale_code        VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    current_version_id VARCHAR(32),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, template_key, channel_code, locale_code)
);

CREATE TABLE IF NOT EXISTS ops_notification_template_version (
    id                   VARCHAR(32) PRIMARY KEY,
    tenant_id            VARCHAR(32) NOT NULL,
    template_id          VARCHAR(32) NOT NULL REFERENCES ops_notification_template(id),
    version_no           INTEGER NOT NULL,
    template_state       VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    subject_template     VARCHAR(256),
    body_template        TEXT NOT NULL,
    variable_schema_json TEXT NOT NULL,
    effective_from       TIMESTAMP,
    effective_until      TIMESTAMP,
    created_by           VARCHAR(32),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, template_id, version_no),
    CHECK (template_state IN ('DRAFT','PENDING_REVIEW','REJECTED','PUBLISHED','EXPIRED','WITHDRAWN'))
);

ALTER TABLE ops_notification_template
    ADD CONSTRAINT fk_ops_template_current_version
    FOREIGN KEY (current_version_id) REFERENCES ops_notification_template_version(id);

CREATE TABLE IF NOT EXISTS ops_notification_routing_policy (
    id                  VARCHAR(32) PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    category_code       VARCHAR(64) NOT NULL,
    priority_code       VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ordered_channels    VARCHAR(160) NOT NULL DEFAULT 'IN_APP',
    fallback_enabled    SMALLINT NOT NULL DEFAULT 0,
    quiet_hours_json    TEXT,
    mandatory_category SMALLINT NOT NULL DEFAULT 0,
    enabled             SMALLINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, category_code, priority_code)
);

CREATE TABLE IF NOT EXISTS ops_notification_user_preference (
    id                VARCHAR(32) PRIMARY KEY,
    tenant_id         VARCHAR(32) NOT NULL,
    user_id           VARCHAR(32) NOT NULL,
    category_code     VARCHAR(64) NOT NULL,
    channel_code      VARCHAR(16) NOT NULL,
    enabled           SMALLINT NOT NULL DEFAULT 1,
    quiet_hours_json  TEXT,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, user_id, category_code, channel_code)
);

-- 热路径索引均以租户开头，避免租户隔离条件遗漏时出现跨租户扫描。
CREATE INDEX IF NOT EXISTS idx_ops_ann_version_active
    ON ops_announcement_version(tenant_id, lifecycle_state, scheduled_publish_at, effective_until);
CREATE INDEX IF NOT EXISTS idx_ops_ann_rule_version
    ON ops_announcement_audience_rule(tenant_id, version_id, selector_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_ops_ann_snapshot_user
    ON ops_announcement_recipient_snapshot(tenant_id, recipient_user_id, version_id);
CREATE INDEX IF NOT EXISTS idx_ops_ann_receipt_deadline
    ON ops_announcement_receipt(tenant_id, version_id, confirmed_at, read_at);
CREATE INDEX IF NOT EXISTS idx_ops_notification_task_retry
    ON ops_notification_task(tenant_id, task_state, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_ops_notification_task_source
    ON ops_notification_task(tenant_id, producer, event_id);
CREATE INDEX IF NOT EXISTS idx_ops_inbox_user_state
    ON ops_inbox_projection(tenant_id, recipient_user_id, hidden_at, read_at, received_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_ops_inbox_source
    ON ops_inbox_projection(tenant_id, source_owner, resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_ops_delivery_task
    ON ops_notification_delivery_attempt(tenant_id, task_id, delivery_status, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_outbox_pending
    ON ops_notification_outbox(tenant_id, published_at, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_ops_inbox_change_user
    ON ops_inbox_change_sequence(tenant_id, recipient_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_template_lookup
    ON ops_notification_template(tenant_id, template_key, channel_code, locale_code);
