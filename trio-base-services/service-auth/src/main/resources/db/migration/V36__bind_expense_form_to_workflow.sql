-- Bind lowcode expense instances to workflow instances and grant launch permission.

ALTER TABLE lc_form_instance
    ADD COLUMN IF NOT EXISTS process_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS process_instance_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_status VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_instance_process
    ON lc_form_instance(process_instance_id)
    WHERE process_instance_id IS NOT NULL;

UPDATE lc_form_definition
SET schema_json = '{"additionalProperties":false,"properties":{"amount":{"minimum":0.01,"title":"金额","type":"number"},"reason":{"maxLength":200,"minLength":2,"title":"事由","type":"string"},"dept":{"minLength":1,"title":"所属部门","type":"string"},"remark":{"maxLength":300,"title":"备注","type":"string"}},"required":["amount","reason","dept"],"title":"费用报销","type":"object"}',
    ui_schema_json = '{"amount":{"ui:placeholder":"请输入报销金额","ui:widget":"money"},"reason":{"ui:placeholder":"请输入报销事由","ui:widget":"textarea"},"dept":{"ui:placeholder":"请输入所属部门"},"remark":{"ui:placeholder":"请输入备注（选填）","ui:widget":"textarea"}}',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE form_key = 'expense';

INSERT INTO lc_form_field_definition (
    id, form_definition_id, field_key, label, field_type, required_flag,
    placeholder, sort_order, created_by, updated_by
)
SELECT 'LC_FIELD_EXPENSE_DEPT', id, 'dept', U&'\6240\5C5E\90E8\95E8', 'text', 1,
       U&'\8BF7\8F93\5165\6240\5C5E\90E8\95E8', 30, 'SYSTEM', 'SYSTEM'
FROM lc_form_definition WHERE form_key = 'expense'
ON CONFLICT (form_definition_id, field_key) DO UPDATE SET
    label = EXCLUDED.label, field_type = EXCLUDED.field_type,
    required_flag = EXCLUDED.required_flag, placeholder = EXCLUDED.placeholder,
    sort_order = EXCLUDED.sort_order, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO lc_form_field_definition (
    id, form_definition_id, field_key, label, field_type, required_flag,
    placeholder, sort_order, created_by, updated_by
)
SELECT 'LC_FIELD_EXPENSE_REMARK', id, 'remark', U&'\5907\6CE8', 'textarea', 0,
       U&'\8BF7\8F93\5165\5907\6CE8', 40, 'SYSTEM', 'SYSTEM'
FROM lc_form_definition WHERE form_key = 'expense'
ON CONFLICT (form_definition_id, field_key) DO UPDATE SET
    label = EXCLUDED.label, field_type = EXCLUDED.field_type,
    required_flag = EXCLUDED.required_flag, placeholder = EXCLUDED.placeholder,
    sort_order = EXCLUDED.sort_order, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

UPDATE wf_process_package
SET form_schema = '{"type":"object","additionalProperties":false,"required":["amount","reason","dept"],"properties":{"amount":{"type":"number","title":"报销金额","minimum":0.01},"reason":{"type":"string","title":"报销事由","minLength":2},"dept":{"type":"string","title":"所属部门","minLength":1},"remark":{"type":"string","title":"备注"},"businessId":{"type":"string","title":"业务单据ID"},"formInstanceId":{"type":"string","title":"表单实例ID"}}}',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE process_key = 'expense_report' AND status = 'PUBLISHED';

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P087', '/api/v1/forms/expense/instances/*/process', 'PUT', 'Bind expense form to workflow instance')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M032_API_BIND_PROCESS', 'M032', 'LowcodeExpenseBindProcess', 'bindExpenseProcess(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 90, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P087', '/api/v1/forms/expense/instances/*/process:PUT',
     'Bind lowcode expense instance to workflow instance')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':' || m.id), 1, 24)), r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.id IN ('R001', 'R002', 'R003')
  AND m.id IN ('M023_BTN_START', 'M032_API_BIND_PROCESS')
ON CONFLICT (role_id, menu_id) DO NOTHING;
