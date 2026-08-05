-- 目的：追加记录收件人证据查看和导出等高敏操作，支持安全调查与保留冻结。
-- 不变量：审计只保存资源标识、动作分类、数量和分页等安全元数据，不保存收件人列表或公告正文。
-- 回滚：安全审计属于合规证据，不随应用回滚删除；停用调用入口后保留表及既有记录。
CREATE TABLE IF NOT EXISTS ops_notification_security_audit (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(32) NOT NULL,
    action_code   VARCHAR(48) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    safe_details  VARCHAR(512),
    trace_id      VARCHAR(128),
    occurred_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_notification_security_audit_tenant_time
    ON ops_notification_security_audit(tenant_id, occurred_at DESC, id);

