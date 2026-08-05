-- 目的：记录每条冻结受众规则的 Owner 游标，使十万级受众解析可在失败后从已提交批次继续。
-- 不变量：游标只对 tenant/version/rule 生效；插入收件快照仍由唯一约束保证重试幂等。
-- 回滚：关闭冻结发布 Worker 后可保留该表，不影响 legacy 或动态公告读取。
CREATE TABLE IF NOT EXISTS ops_announcement_snapshot_checkpoint (
    id               VARCHAR(32) PRIMARY KEY,
    tenant_id        VARCHAR(32) NOT NULL,
    version_id       VARCHAR(32) NOT NULL REFERENCES ops_announcement_version(id),
    rule_id          VARCHAR(32) NOT NULL REFERENCES ops_announcement_audience_rule(id),
    cursor_value     VARCHAR(256),
    checkpoint_state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    resolved_count   BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, version_id, rule_id),
    CHECK (checkpoint_state IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_ops_ann_snapshot_checkpoint
    ON ops_announcement_snapshot_checkpoint(tenant_id, version_id, checkpoint_state, updated_at);
