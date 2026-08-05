-- 目的：为 service-ops 已知租户补齐统一渠道目录，避免迁移后出现“无渠道”歧义。
-- 不变量：IN_APP 是首期唯一 READY 渠道；外部渠道在适配器验证前只能是 NOT_CONNECTED。
-- 回滚：目录记录可按本迁移生成规则删除，但不得在已经产生配置或投递证据后回滚。
WITH tenants AS (
    SELECT DISTINCT tenant_id FROM ops_announcement WHERE tenant_id IS NOT NULL
    UNION SELECT DISTINCT tenant_id FROM ops_message WHERE tenant_id IS NOT NULL
    UNION SELECT DISTINCT tenant_id FROM ops_notification_task WHERE tenant_id IS NOT NULL
), channels(channel_code, capability_state, desired_enabled) AS (
    VALUES ('IN_APP', 'READY', 1),
           ('EMAIL', 'NOT_CONNECTED', 0),
           ('SMS', 'NOT_CONNECTED', 0),
           ('WE_COM', 'NOT_CONNECTED', 0),
           ('DINGTALK', 'NOT_CONNECTED', 0)
)
INSERT INTO ops_notification_channel
    (id, tenant_id, channel_code, capability_state, desired_enabled, created_at, updated_at)
SELECT md5(tenants.tenant_id || ':' || channels.channel_code), tenants.tenant_id,
       channels.channel_code, channels.capability_state, channels.desired_enabled,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants CROSS JOIN channels
ON CONFLICT (tenant_id, channel_code) DO NOTHING;

