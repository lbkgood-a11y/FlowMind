-- 目的：为通知投影、回执、投递、公告与审计证据提供租户级保留策略和法律/审计冻结。
-- 不变量：冻结优先于到期时间；清理只能在单一 tenant_id 内按稳定主键小批执行，公告仅匿名化而不破坏证据链。
-- 回滚：停用调度器即可停止清理；已删除或匿名化的数据不可由 DDL 回滚恢复，生产启用前必须完成策略审批和备份演练。
CREATE TABLE IF NOT EXISTS ops_notification_retention_policy (
    id                    VARCHAR(32) PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    projection_days       INTEGER NOT NULL DEFAULT 730,
    receipt_days          INTEGER NOT NULL DEFAULT 1095,
    delivery_days         INTEGER NOT NULL DEFAULT 1095,
    announcement_days     INTEGER NOT NULL DEFAULT 3650,
    audit_days            INTEGER NOT NULL DEFAULT 3650,
    purge_batch_size      INTEGER NOT NULL DEFAULT 500,
    enabled               SMALLINT NOT NULL DEFAULT 1,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id),
    CHECK (projection_days BETWEEN 1 AND 36500),
    CHECK (receipt_days BETWEEN 1 AND 36500),
    CHECK (delivery_days BETWEEN 1 AND 36500),
    CHECK (announcement_days BETWEEN 1 AND 36500),
    CHECK (audit_days BETWEEN 1 AND 36500),
    CHECK (purge_batch_size BETWEEN 1 AND 5000)
);

CREATE TABLE IF NOT EXISTS ops_notification_retention_hold (
    id           VARCHAR(32) PRIMARY KEY,
    tenant_id    VARCHAR(32) NOT NULL,
    scope_type   VARCHAR(32) NOT NULL,
    scope_id     VARCHAR(64) NOT NULL,
    hold_type    VARCHAR(16) NOT NULL,
    reason       VARCHAR(512) NOT NULL,
    expires_at   TIMESTAMP,
    released_at  TIMESTAMP,
    created_by   VARCHAR(32) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, scope_type, scope_id, hold_type),
    CHECK (scope_type IN ('TASK','ANNOUNCEMENT','AUDIT')),
    CHECK (hold_type IN ('LEGAL','AUDIT'))
);

ALTER TABLE ops_announcement_version
    ADD COLUMN IF NOT EXISTS retention_anonymized_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_ops_retention_hold_active
    ON ops_notification_retention_hold(tenant_id, scope_type, scope_id, released_at, expires_at);
CREATE INDEX IF NOT EXISTS idx_ops_inbox_retention
    ON ops_inbox_projection(tenant_id, received_at, id);
CREATE INDEX IF NOT EXISTS idx_ops_receipt_retention
    ON ops_announcement_receipt(tenant_id, updated_at, id);
CREATE INDEX IF NOT EXISTS idx_ops_delivery_retention
    ON ops_notification_delivery_attempt(tenant_id, occurred_at, id);
CREATE INDEX IF NOT EXISTS idx_ops_announcement_retention
    ON ops_announcement_version(tenant_id, updated_at, retention_anonymized_at, id);

