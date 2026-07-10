-- V1__auth_schema.sql — 认证中心初始 Schema（RBAC 五表 + Flyway 管理表）
CREATE TABLE IF NOT EXISTS sys_user (
    id          VARCHAR(32)  PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(256) NOT NULL,
    email       VARCHAR(128),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username ON sys_user(username);

CREATE TABLE IF NOT EXISTS sys_role (
    id          VARCHAR(32)  PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_code ON sys_role(role_code);

CREATE TABLE IF NOT EXISTS sys_permission (
    id          VARCHAR(32)  PRIMARY KEY,
    resource    VARCHAR(256) NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_res_act ON sys_permission(resource, action);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id VARCHAR(32) NOT NULL REFERENCES sys_user(id),
    role_id VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    permission_id VARCHAR(32) NOT NULL REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

-- 默认角色与管理员
INSERT INTO sys_role(id, role_code, role_name, description) VALUES
    ('01HK153X020000000000000002', 'ADMIN',    '超级管理员', '拥有全部权限'),
    ('01HK153X030000000000000003', 'TENANT_ADMIN', '租户管理员', '管理本租户用户与资源'),
    ('01HK153X040000000000000004', 'USER',     '普通用户',   '基本功能访问')
ON CONFLICT DO NOTHING;

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('01HK153X050000000000000005', '/api/v1/users',    'GET',    '查看用户列表'),
    ('01HK153X060000000000000006', '/api/v1/users',    'POST',   '创建用户'),
    ('01HK153X070000000000000007', '/api/v1/users/*',  'PUT',    '编辑用户'),
    ('01HK153X080000000000000008', '/api/v1/users/*',  'DELETE', '删除用户'),
    ('01HK153X090000000000000009', '/api/v1/roles',    'GET',    '查看角色列表'),
    ('01HK153X0A000000000000000A', '/api/v1/roles',    'POST',   '创建角色'),
    ('01HK153X0B000000000000000B', '/api/v1/roles/*',  'PUT',    '编辑角色'),
    ('01HK153X0C000000000000000C', '/api/v1/roles/*',  'DELETE', '删除角色'),
    ('01HK153X0D000000000000000D', '/api/v1/ai/chat',  'POST',   'AI 对话'),
    ('01HK153X0E000000000000000E', '/api/v1/rag/**',   'GET',    '知识库检索')
ON CONFLICT DO NOTHING;

-- 默认管理员用户（密码 admin123 的 BCrypt 哈希，部署后请修改）
INSERT INTO sys_user(id, username, password, email, status) VALUES
    ('01HK153X010000000000000001', 'admin', '$2a$10$sSzlRIHLEd3J4Vutq/w4XemoxUwwgq0aA.qzsNaSp.YPkzLRmtjCy', 'admin@triobase.local', 1)
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role(user_id, role_id) VALUES ('01HK153X010000000000000001', '01HK153X020000000000000002')
ON CONFLICT DO NOTHING;
