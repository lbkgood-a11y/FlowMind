-- Organization dimensions, per-dimension tree relations, and richer user assignments.

ALTER TABLE sys_org_unit
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS unit_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT';

DROP INDEX IF EXISTS uk_sys_org_unit_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_org_unit_tenant_code ON sys_org_unit(tenant_id, unit_code);
CREATE INDEX IF NOT EXISTS idx_sys_org_unit_type_status ON sys_org_unit(unit_type, status);

CREATE TABLE IF NOT EXISTS sys_org_dimension (
    id             VARCHAR(32)  PRIMARY KEY,
    tenant_id      VARCHAR(32)  NOT NULL DEFAULT 'default',
    dimension_code VARCHAR(64)  NOT NULL,
    dimension_name VARCHAR(128) NOT NULL,
    is_default     SMALLINT     NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    sort_order     INTEGER      NOT NULL DEFAULT 100,
    description    VARCHAR(256),
    created_by     VARCHAR(32),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(32),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_org_dimension_tenant_code
    ON sys_org_dimension(tenant_id, dimension_code);
CREATE INDEX IF NOT EXISTS idx_sys_org_dimension_sort
    ON sys_org_dimension(tenant_id, status, sort_order);

INSERT INTO sys_org_dimension(
    id, tenant_id, dimension_code, dimension_name, is_default, status, sort_order, description, created_by, updated_by
) VALUES
    ('ORG_DIM_ADMIN', 'default', 'ADMIN', '行政组织', 1, 1, 10, '用户归属、通讯录和默认审批线', 'SYSTEM', 'SYSTEM'),
    ('ORG_DIM_LEGAL', 'default', 'LEGAL', '法人组织', 0, 1, 20, '合同主体、开票主体和法务边界', 'SYSTEM', 'SYSTEM'),
    ('ORG_DIM_ACCOUNTING', 'default', 'ACCOUNTING', '核算组织', 0, 1, 30, '成本中心、利润中心和财务核算', 'SYSTEM', 'SYSTEM'),
    ('ORG_DIM_BUSINESS', 'default', 'BUSINESS', '业务组织', 0, 1, 40, '销售区域、事业部和业务看板', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    dimension_code = EXCLUDED.dimension_code,
    dimension_name = EXCLUDED.dimension_name,
    is_default = EXCLUDED.is_default,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS sys_org_relation (
    id             VARCHAR(32) PRIMARY KEY,
    tenant_id      VARCHAR(32) NOT NULL DEFAULT 'default',
    dimension_id   VARCHAR(32) NOT NULL REFERENCES sys_org_dimension(id),
    parent_unit_id VARCHAR(32) REFERENCES sys_org_unit(id),
    child_unit_id  VARCHAR(32) NOT NULL REFERENCES sys_org_unit(id),
    tree_path      VARCHAR(512) NOT NULL,
    level          INTEGER     NOT NULL DEFAULT 1,
    sort_order     INTEGER     NOT NULL DEFAULT 100,
    status         SMALLINT    NOT NULL DEFAULT 1,
    created_by     VARCHAR(32),
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(32),
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_org_relation_dimension_child
    ON sys_org_relation(dimension_id, child_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_org_relation_parent_sort
    ON sys_org_relation(dimension_id, parent_unit_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_sys_org_relation_path
    ON sys_org_relation(dimension_id, tree_path);

INSERT INTO sys_org_relation(
    id, tenant_id, dimension_id, parent_unit_id, child_unit_id, tree_path, level, sort_order, status, created_by, updated_by
)
SELECT 'OR' || upper(substr(md5('ORG_DIM_ADMIN' || ':' || u.id), 1, 24)),
       u.tenant_id,
       'ORG_DIM_ADMIN',
       u.parent_id,
       u.id,
       COALESCE(NULLIF(u.tree_path, ''), '/' || u.id),
       COALESCE(array_length(string_to_array(trim(both '/' from COALESCE(NULLIF(u.tree_path, ''), u.id)), '/'), 1), 1),
       u.sort_order,
       u.status,
       'SYSTEM',
       'SYSTEM'
FROM sys_org_unit u
ON CONFLICT (dimension_id, child_unit_id) DO UPDATE SET
    parent_unit_id = EXCLUDED.parent_unit_id,
    tree_path = EXCLUDED.tree_path,
    level = EXCLUDED.level,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE sys_user_org_unit
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS dimension_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS is_primary SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS position_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS position_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_leader SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS effective_from DATE,
    ADD COLUMN IF NOT EXISTS effective_to DATE,
    ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 1;

UPDATE sys_user_org_unit
SET tenant_id = COALESCE(tenant_id, 'default'),
    dimension_id = COALESCE(dimension_id, 'ORG_DIM_ADMIN')
WHERE dimension_id IS NULL OR tenant_id IS NULL;

WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY user_id, dimension_id ORDER BY created_at ASC, id ASC) AS rn
    FROM sys_user_org_unit
)
UPDATE sys_user_org_unit u
SET is_primary = CASE WHEN ranked.rn = 1 THEN 1 ELSE 0 END
FROM ranked
WHERE ranked.id = u.id
  AND u.dimension_id = 'ORG_DIM_ADMIN';

ALTER TABLE sys_user_org_unit
    ALTER COLUMN dimension_id SET NOT NULL;

DROP INDEX IF EXISTS uk_sys_user_org_unit;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_org_dimension_unit
    ON sys_user_org_unit(tenant_id, user_id, dimension_id, org_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_org_dimension_user
    ON sys_user_org_unit(tenant_id, dimension_id, user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_org_dimension_org
    ON sys_user_org_unit(tenant_id, dimension_id, org_unit_id);
