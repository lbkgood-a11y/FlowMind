CREATE TABLE IF NOT EXISTS sys_tenant (
    id               VARCHAR(32)  PRIMARY KEY,
    tenant_code      VARCHAR(32)  NOT NULL,
    tenant_name      VARCHAR(128) NOT NULL,
    short_name       VARCHAR(64),
    tenant_type      VARCHAR(32)  NOT NULL DEFAULT 'STANDARD',
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    isolation_mode   VARCHAR(32)  NOT NULL DEFAULT 'SHARED_SCHEMA',
    contact_name     VARCHAR(64),
    contact_email    VARCHAR(128),
    contact_phone    VARCHAR(32),
    region           VARCHAR(64),
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    locale           VARCHAR(32)  NOT NULL DEFAULT 'zh-CN',
    industry         VARCHAR(64),
    plan_code        VARCHAR(64)  NOT NULL DEFAULT 'BASIC',
    max_users        INTEGER      NOT NULL DEFAULT 100,
    expire_at        TIMESTAMP,
    suspended_reason VARCHAR(256),
    attributes_json  TEXT,
    created_by       VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_tenant_code ON sys_tenant(tenant_code);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_plan ON sys_tenant(plan_code, status);

CREATE TABLE IF NOT EXISTS sys_tenant_setting (
    id             VARCHAR(32)  PRIMARY KEY,
    tenant_id      VARCHAR(32)  NOT NULL REFERENCES sys_tenant(id),
    setting_key    VARCHAR(128) NOT NULL,
    setting_value  TEXT,
    value_type     VARCHAR(32)  NOT NULL DEFAULT 'STRING',
    sensitive_flag SMALLINT     NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    description    VARCHAR(256),
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_tenant_setting_key ON sys_tenant_setting(tenant_id, setting_key);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_setting_status ON sys_tenant_setting(tenant_id, status, setting_key);

INSERT INTO sys_tenant (
    id, tenant_code, tenant_name, short_name, tenant_type, status, isolation_mode,
    contact_name, contact_email, region, timezone, locale, industry, plan_code,
    max_users, attributes_json, created_by, updated_by
) VALUES (
    'default', 'default', 'TrioBase 默认租户', '默认租户', 'PLATFORM', 'ACTIVE', 'SHARED_SCHEMA',
    'System Administrator', 'admin@triobase.local', 'CN', 'Asia/Shanghai', 'zh-CN', 'AI Platform',
    'ENTERPRISE', 1000, '{"seed":"tenant-service-v1"}', 'SYSTEM', 'SYSTEM'
)
ON CONFLICT (id) DO UPDATE SET
    tenant_code = EXCLUDED.tenant_code,
    tenant_name = EXCLUDED.tenant_name,
    short_name = EXCLUDED.short_name,
    tenant_type = EXCLUDED.tenant_type,
    status = 'ACTIVE',
    isolation_mode = EXCLUDED.isolation_mode,
    timezone = EXCLUDED.timezone,
    locale = EXCLUDED.locale,
    plan_code = EXCLUDED.plan_code,
    max_users = EXCLUDED.max_users,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_tenant_setting (
    id, tenant_id, setting_key, setting_value, value_type, sensitive_flag, status, description, created_by, updated_by
) VALUES
    ('TS_DEFAULT_LOCALE', 'default', 'tenant.locale', 'zh-CN', 'STRING', 0, 1, '默认界面语言', 'SYSTEM', 'SYSTEM'),
    ('TS_DEFAULT_TIMEZONE', 'default', 'tenant.timezone', 'Asia/Shanghai', 'STRING', 0, 1, '默认时区', 'SYSTEM', 'SYSTEM'),
    ('TS_DEFAULT_AI_MASKING', 'default', 'ai.masking.enabled', 'true', 'BOOLEAN', 0, 1, 'AI 网关双重脱敏开关', 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
