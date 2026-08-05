-- 目的：支持多个 service-ops 实例安全竞争 Outbox，崩溃实例的锁可在超时后被接管。
-- 不变量：锁只协调发布尝试；event_id 和下游幂等键仍是重复发布的最终防线。
-- 回滚：停用 Kafka dispatcher 后新增列可保留，不改变通知任务或 legacy 路径。
ALTER TABLE ops_notification_outbox ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE ops_notification_outbox ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_ops_outbox_claim
    ON ops_notification_outbox(published_at, next_attempt_at, locked_at, created_at);
