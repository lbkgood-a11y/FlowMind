CREATE TABLE IF NOT EXISTS sys_org_unit (
    id          VARCHAR(32)  PRIMARY KEY,
    parent_id   VARCHAR(32)  REFERENCES sys_org_unit(id),
    unit_code   VARCHAR(64)  NOT NULL,
    unit_name   VARCHAR(128) NOT NULL,
    tree_path   VARCHAR(512),
    sort_order  INTEGER      NOT NULL DEFAULT 100,
    status      SMALLINT     NOT NULL DEFAULT 1,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_org_unit_code ON sys_org_unit(unit_code);
CREATE INDEX IF NOT EXISTS idx_sys_org_parent_sort ON sys_org_unit(parent_id, sort_order);

CREATE TABLE IF NOT EXISTS sys_user_org_unit (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    org_unit_id VARCHAR(32) NOT NULL REFERENCES sys_org_unit(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_org_unit ON sys_user_org_unit(user_id, org_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_org_user ON sys_user_org_unit(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_org_org ON sys_user_org_unit(org_unit_id);

INSERT INTO sys_org_unit(id, parent_id, unit_code, unit_name, tree_path, sort_order, status, description) VALUES
    ('O001', NULL,   'HQ',      '总部',       '/O001',             10, 1, 'TrioBase 总部'),
    ('O002', 'O001', 'SALES',   '销售中心',   '/O001/O002',        20, 1, '销售与客户运营'),
    ('O003', 'O001', 'FINANCE', '财务中心',   '/O001/O003',        30, 1, '财务与结算管理'),
    ('O004', 'O001', 'TECH',    '技术中心',   '/O001/O004',        40, 1, '平台研发与交付'),
    ('O005', 'O004', 'FE',      '前端组',     '/O001/O004/O005',   50, 1, '前端体验与工作台'),
    ('O006', 'O004', 'BE',      '后端组',     '/O001/O004/O006',   60, 1, '服务与数据接口')
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_org_unit(id, user_id, org_unit_id) VALUES
    ('UO001', 'U001', 'O001'),
    ('UO002', 'U001', 'O004'),
    ('UO003', 'U001', 'O006')
ON CONFLICT DO NOTHING;
