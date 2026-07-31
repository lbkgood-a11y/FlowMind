-- Convert legacy global roles into tenant-owned roles without widening access.
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE sys_role SET tenant_id = 'default' WHERE tenant_id IS NULL OR btrim(tenant_id) = '';
ALTER TABLE sys_role ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE sys_role ALTER COLUMN tenant_id SET NOT NULL;

DROP INDEX IF EXISTS uk_sys_role_code;

CREATE TEMP TABLE tmp_role_tenant_map ON COMMIT DROP AS
WITH role_usage AS (
    SELECT DISTINCT u.tenant_id, ur.role_id
      FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id
    UNION SELECT DISTINCT tenant_id, subject_id FROM sys_auth_grant WHERE subject_type = 'ROLE'
    UNION SELECT DISTINCT tenant_id, subject_id FROM sys_auth_field_policy WHERE subject_type = 'ROLE'
    UNION SELECT DISTINCT tenant_id, subject_id FROM sys_data_policy WHERE subject_type = 'ROLE'
    UNION SELECT DISTINCT tenant_id, role_id FROM sys_role_auth_draft
    UNION SELECT DISTINCT tenant_id, role_id FROM sys_role_auth_release
    UNION SELECT DISTINCT tenant_id, role_id FROM sys_role_auth_active_release
    UNION SELECT DISTINCT tenant_id, role_id FROM sys_role_auth_drift
    UNION SELECT DISTINCT tenant_id, role_id FROM sys_role_auth_audit
)
SELECT usage.tenant_id,
       usage.role_id AS old_role_id,
       CASE WHEN usage.tenant_id = role_row.tenant_id THEN role_row.id
            ELSE 'RT' || upper(substr(md5(usage.tenant_id || ':' || role_row.id), 1, 24)) END AS new_role_id
  FROM role_usage usage
  JOIN sys_role role_row ON role_row.id = usage.role_id
 WHERE usage.tenant_id IS NOT NULL AND btrim(usage.tenant_id) <> '';

INSERT INTO sys_role(id, tenant_id, role_code, role_name, description, status,
                     created_by, created_at, updated_by, updated_at)
SELECT DISTINCT mapping.new_role_id, mapping.tenant_id, role_row.role_code, role_row.role_name,
       role_row.description, role_row.status, 'SYSTEM_TENANT_MIGRATION', CURRENT_TIMESTAMP,
       'SYSTEM_TENANT_MIGRATION', CURRENT_TIMESTAMP
  FROM tmp_role_tenant_map mapping
  JOIN sys_role role_row ON role_row.id = mapping.old_role_id
 WHERE mapping.new_role_id <> mapping.old_role_id
ON CONFLICT (id) DO NOTHING;

UPDATE sys_user_role relation
   SET role_id = mapping.new_role_id
  FROM sys_user user_row, tmp_role_tenant_map mapping
 WHERE user_row.id = relation.user_id
   AND mapping.tenant_id = user_row.tenant_id
   AND mapping.old_role_id = relation.role_id
   AND mapping.new_role_id <> mapping.old_role_id;

UPDATE sys_auth_grant item SET subject_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.subject_type = 'ROLE'
   AND item.subject_id = mapping.old_role_id AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_auth_field_policy item SET subject_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.subject_type = 'ROLE'
   AND item.subject_id = mapping.old_role_id AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_data_policy item SET subject_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.subject_type = 'ROLE'
   AND item.subject_id = mapping.old_role_id AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_role_auth_draft item SET role_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.role_id = mapping.old_role_id
   AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_role_auth_release item SET role_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.role_id = mapping.old_role_id
   AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_role_auth_active_release item SET role_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.role_id = mapping.old_role_id
   AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_role_auth_drift item SET role_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.role_id = mapping.old_role_id
   AND mapping.new_role_id <> mapping.old_role_id;
UPDATE sys_role_auth_audit item SET role_id = mapping.new_role_id
  FROM tmp_role_tenant_map mapping
 WHERE item.tenant_id = mapping.tenant_id AND item.role_id = mapping.old_role_id
   AND mapping.new_role_id <> mapping.old_role_id;

-- Menu and permission projections already use sys_auth_grant as their single source.
-- The subject-id update above preserves those projections for each cloned tenant role.

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_tenant_code ON sys_role(tenant_id, role_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_tenant_id ON sys_role(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_sys_role_tenant_status ON sys_role(tenant_id, status);

ALTER TABLE sys_role_auth_draft DROP CONSTRAINT IF EXISTS sys_role_auth_draft_role_id_fkey;
ALTER TABLE sys_role_auth_release DROP CONSTRAINT IF EXISTS sys_role_auth_release_role_id_fkey;
ALTER TABLE sys_role_auth_active_release DROP CONSTRAINT IF EXISTS sys_role_auth_active_release_role_id_fkey;

ALTER TABLE sys_role_auth_draft
    ADD CONSTRAINT fk_role_auth_draft_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES sys_role(tenant_id, id);
ALTER TABLE sys_role_auth_release
    ADD CONSTRAINT fk_role_auth_release_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES sys_role(tenant_id, id);
ALTER TABLE sys_role_auth_active_release
    ADD CONSTRAINT fk_role_auth_active_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES sys_role(tenant_id, id);
