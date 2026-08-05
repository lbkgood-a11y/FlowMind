-- 目的：为渠道、凭据绑定、模板、路由和用户偏好提供追加式租户审计证据。
-- 不变量：details 只允许安全分类摘要，禁止写入凭据引用、secret 值、模板正文或 provider settings。
-- 回滚：审计属于合规证据；生产环境不得通过回滚迁移删除，需遵循后续保留策略。
CREATE TABLE IF NOT EXISTS ops_notification_config_audit (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_key  VARCHAR(256) NOT NULL,
    action_code   VARCHAR(48) NOT NULL,
    safe_details  VARCHAR(512),
    trace_id      VARCHAR(64),
    occurred_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_notification_config_audit_tenant_time
    ON ops_notification_config_audit(tenant_id, occurred_at DESC, id);

