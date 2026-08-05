-- 目的：以稳定映射把 legacy 公告/消息投影到 v2 结构，支持双读期间逐租户核对与回滚。
-- 数据假设：legacy ID 在租户内唯一；逗号分隔受众只包含组织或用户 ID，不推断角色或动态参与者。
-- 回滚：v1 表保持不变；关闭 v2 read/dual-write 后可忽略新结构，映射表用于重跑和问题定位。

CREATE TABLE IF NOT EXISTS ops_notification_legacy_map (
    id             VARCHAR(32) PRIMARY KEY,
    tenant_id      VARCHAR(32) NOT NULL,
    legacy_type    VARCHAR(32) NOT NULL,
    legacy_id      VARCHAR(32) NOT NULL,
    v2_type        VARCHAR(32) NOT NULL,
    v2_id          VARCHAR(32) NOT NULL,
    migrated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, legacy_type, legacy_id, v2_type)
);

INSERT INTO ops_announcement_identity (
    id, tenant_id, announcement_code, legacy_id, created_by, created_at, updated_by, updated_at)
SELECT upper(md5(a.tenant_id || ':announcement:' || a.id)),
       a.tenant_id,
       'LEGACY-' || a.id,
       a.id,
       a.created_by,
       a.created_at,
       a.updated_by,
       a.updated_at
FROM ops_announcement a
ON CONFLICT (tenant_id, legacy_id) DO NOTHING;

INSERT INTO ops_announcement_version (
    id, tenant_id, announcement_id, version_no, title, content, priority, lifecycle_state,
    audience_mode, confirmation_required, scheduled_publish_at, published_at, effective_until,
    created_by, created_at, updated_by, updated_at)
SELECT upper(md5(a.tenant_id || ':announcement-version:' || a.id || ':1')),
       a.tenant_id,
       i.id,
       1,
       a.title,
       a.content,
       a.priority,
       CASE a.status
           WHEN 'PUBLISHED' THEN 'PUBLISHED'
           WHEN 'OFFLINE' THEN 'WITHDRAWN'
           ELSE 'DRAFT'
       END,
       'DYNAMIC',
       0,
       a.publish_at,
       a.publish_at,
       a.unpublish_at,
       a.created_by,
       a.created_at,
       a.updated_by,
       a.updated_at
FROM ops_announcement a
JOIN ops_announcement_identity i
  ON i.tenant_id = a.tenant_id AND i.legacy_id = a.id
ON CONFLICT (tenant_id, announcement_id, version_no) DO NOTHING;

UPDATE ops_announcement_identity i
SET current_version_id = v.id
FROM ops_announcement_version v
WHERE v.tenant_id = i.tenant_id
  AND v.announcement_id = i.id
  AND v.version_no = 1
  AND i.current_version_id IS NULL;

-- ALL 仅产生一条规则；组织和用户 CSV 被逐项展开，空项不会变成授权范围。
INSERT INTO ops_announcement_audience_rule (
    id, tenant_id, version_id, selector_type, subject_id, include_descendants, created_by, created_at)
SELECT upper(md5(a.tenant_id || ':announcement-rule:' || a.id || ':' || a.target_type || ':' || coalesce(target.subject_id, 'ALL'))),
       a.tenant_id,
       v.id,
       CASE a.target_type WHEN 'ORG' THEN 'ORGANIZATION' WHEN 'USER' THEN 'USER' ELSE 'ALL' END,
       target.subject_id,
       0,
       a.created_by,
       a.created_at
FROM ops_announcement a
JOIN ops_announcement_identity i
  ON i.tenant_id = a.tenant_id AND i.legacy_id = a.id
JOIN ops_announcement_version v
  ON v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
LEFT JOIN LATERAL (
    SELECT nullif(trim(value), '') AS subject_id
    FROM unnest(string_to_array(
        CASE a.target_type WHEN 'ORG' THEN a.target_org_ids WHEN 'USER' THEN a.target_user_ids ELSE NULL END,
        ',')) value
) target ON a.target_type IN ('ORG', 'USER')
WHERE a.target_type = 'ALL' OR target.subject_id IS NOT NULL
ON CONFLICT (id) DO NOTHING;

INSERT INTO ops_announcement_receipt (
    id, tenant_id, version_id, recipient_user_id, read_at, created_at, updated_at)
SELECT upper(md5(r.tenant_id || ':announcement-receipt:' || r.id)),
       r.tenant_id,
       v.id,
       r.user_id,
       r.read_at,
       r.created_at,
       r.updated_at
FROM ops_announcement_read r
JOIN ops_announcement_identity i
  ON i.tenant_id = r.tenant_id AND i.legacy_id = r.announcement_id
JOIN ops_announcement_version v
  ON v.tenant_id = i.tenant_id AND v.announcement_id = i.id AND v.version_no = 1
ON CONFLICT (tenant_id, version_id, recipient_user_id) DO NOTHING;

INSERT INTO ops_notification_task (
    id, tenant_id, producer, event_id, idempotency_key, schema_version, template_key,
    request_payload, task_state, audience_mode, resolved_count, delivered_count, trace_id,
    created_at, updated_at)
SELECT upper(md5(m.tenant_id || ':message-task:' || m.id)),
       m.tenant_id,
       coalesce(nullif(m.source_type, ''), 'legacy-service-ops'),
       'legacy-message:' || m.id,
       'legacy-message:' || m.id,
       '1.0',
       'legacy-' || lower(m.message_type),
       jsonb_build_object(
           'legacyMessageId', m.id,
           'title', m.title,
           'messageType', m.message_type,
           'sourceType', m.source_type,
           'sourceId', m.source_id)::text,
       'DELIVERED',
       'FROZEN',
       count(r.id),
       count(r.id),
       NULL,
       m.created_at,
       m.updated_at
FROM ops_message m
LEFT JOIN ops_message_recipient r
  ON r.tenant_id = m.tenant_id AND r.message_id = m.id
GROUP BY m.id, m.tenant_id, m.source_type, m.source_id, m.title, m.message_type, m.created_at, m.updated_at
ON CONFLICT (tenant_id, producer, idempotency_key) DO NOTHING;

INSERT INTO ops_inbox_projection (
    id, tenant_id, task_id, recipient_user_id, channel_code, item_type, title, summary,
    source_owner, resource_type, resource_id, read_at, hidden_at, received_at, created_at, updated_at)
SELECT upper(md5(r.tenant_id || ':message-projection:' || r.id)),
       r.tenant_id,
       t.id,
       r.recipient_user_id,
       'IN_APP',
       m.message_type,
       m.title,
       left(m.content, 512),
       m.source_type,
       m.source_type,
       m.source_id,
       r.read_at,
       r.deleted_at,
       r.created_at,
       r.created_at,
       r.updated_at
FROM ops_message_recipient r
JOIN ops_message m
  ON m.tenant_id = r.tenant_id AND m.id = r.message_id
JOIN ops_notification_task t
  ON t.tenant_id = m.tenant_id AND t.idempotency_key = 'legacy-message:' || m.id
ON CONFLICT (tenant_id, task_id, recipient_user_id, channel_code) DO NOTHING;

INSERT INTO ops_notification_legacy_map (id, tenant_id, legacy_type, legacy_id, v2_type, v2_id)
SELECT upper(md5(i.tenant_id || ':map:announcement:' || i.legacy_id)),
       i.tenant_id, 'ANNOUNCEMENT', i.legacy_id, 'ANNOUNCEMENT_IDENTITY', i.id
FROM ops_announcement_identity i
WHERE i.legacy_id IS NOT NULL
ON CONFLICT (tenant_id, legacy_type, legacy_id, v2_type) DO NOTHING;

INSERT INTO ops_notification_legacy_map (id, tenant_id, legacy_type, legacy_id, v2_type, v2_id)
SELECT upper(md5(m.tenant_id || ':map:message:' || m.id)),
       m.tenant_id, 'MESSAGE', m.id, 'NOTIFICATION_TASK', t.id
FROM ops_message m
JOIN ops_notification_task t
  ON t.tenant_id = m.tenant_id AND t.idempotency_key = 'legacy-message:' || m.id
ON CONFLICT (tenant_id, legacy_type, legacy_id, v2_type) DO NOTHING;

-- 运维核对视图只返回租户级计数，不暴露个人收件或阅读明细。
CREATE OR REPLACE VIEW ops_notification_migration_verification AS
SELECT tenants.tenant_id,
       (SELECT count(*) FROM ops_announcement a WHERE a.tenant_id = tenants.tenant_id AND a.status = 'PUBLISHED')
           AS legacy_published_announcements,
       (SELECT count(*) FROM ops_announcement_version v WHERE v.tenant_id = tenants.tenant_id AND v.lifecycle_state = 'PUBLISHED')
           AS v2_published_announcements,
       (SELECT count(*) FROM ops_message_recipient r WHERE r.tenant_id = tenants.tenant_id)
           AS legacy_message_recipients,
       (SELECT count(*) FROM ops_inbox_projection p WHERE p.tenant_id = tenants.tenant_id)
           AS v2_message_recipients,
       (SELECT count(*) FROM ops_message_recipient r WHERE r.tenant_id = tenants.tenant_id AND r.read_status = 0 AND r.deleted_at IS NULL)
           AS legacy_unread,
       (SELECT count(*) FROM ops_inbox_projection p WHERE p.tenant_id = tenants.tenant_id AND p.read_at IS NULL AND p.hidden_at IS NULL)
           AS v2_unread,
       (SELECT count(*) FROM ops_announcement_read r WHERE r.tenant_id = tenants.tenant_id)
           AS legacy_announcement_reads,
       (SELECT count(*) FROM ops_announcement_receipt r WHERE r.tenant_id = tenants.tenant_id AND r.read_at IS NOT NULL)
           AS v2_announcement_reads
FROM (
    SELECT tenant_id FROM ops_announcement
    UNION
    SELECT tenant_id FROM ops_message
) tenants;

CREATE INDEX IF NOT EXISTS idx_ops_legacy_map_lookup
    ON ops_notification_legacy_map(tenant_id, legacy_type, legacy_id);
