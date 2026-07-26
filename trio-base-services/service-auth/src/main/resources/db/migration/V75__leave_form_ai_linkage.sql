-- Leave request runtime application and AI linkage seed.
-- The AI agent already classifies the "leave" domain; this migration provides
-- the lowcode runtime contract, workflow package, and authorization closure.

WITH form_seed AS (
    SELECT
        'LC_FORM_LEAVE_001'::varchar AS id,
        'GLOBAL'::varchar AS tenant_id,
        'leave'::varchar AS form_key,
        U&'\8BF7\5047\7533\8BF7'::varchar AS name,
        U&'\7528\4E8E AI \52A9\624B\548C\4F4E\4EE3\7801\8FD0\884C\65F6\7684\8BF7\5047\7533\8BF7\8868\5355'::varchar AS description,
        1::integer AS version,
        'PUBLISHED'::varchar AS status,
        '{"type":"object","title":"\u8bf7\u5047\u7533\u8bf7","additionalProperties":false,"required":["leave_type","start_date","end_date","reason"],"properties":{"leave_type":{"type":"string","title":"\u8bf7\u5047\u7c7b\u578b","enum":["\u4e8b\u5047","\u75c5\u5047","\u5e74\u5047","\u8c03\u4f11","\u5a5a\u5047","\u4ea7\u5047"]},"start_date":{"type":"string","format":"date","title":"\u5f00\u59cb\u65e5\u671f"},"end_date":{"type":"string","format":"date","title":"\u7ed3\u675f\u65e5\u671f"},"reason":{"type":"string","title":"\u8bf7\u5047\u539f\u56e0","minLength":2,"maxLength":200},"remark":{"type":"string","title":"\u5907\u6ce8","maxLength":300}}}'::text AS schema_json,
        '{"leave_type":{"ui:placeholder":"\u8bf7\u9009\u62e9\u8bf7\u5047\u7c7b\u578b","ui:widget":"select"},"start_date":{"ui:placeholder":"\u8bf7\u9009\u62e9\u5f00\u59cb\u65e5\u671f","ui:widget":"date"},"end_date":{"ui:placeholder":"\u8bf7\u9009\u62e9\u7ed3\u675f\u65e5\u671f","ui:widget":"date"},"reason":{"ui:placeholder":"\u8bf7\u8f93\u5165\u8bf7\u5047\u539f\u56e0","ui:widget":"textarea"},"remark":{"ui:placeholder":"\u8bf7\u8f93\u5165\u5907\u6ce8\uff08\u9009\u586b\uff09","ui:widget":"textarea"}}'::text AS ui_schema_json
)
INSERT INTO lc_form_definition (
    id, tenant_id, form_key, name, description, version, status,
    schema_json, ui_schema_json, schema_hash, published_at,
    created_by, created_at, updated_by, updated_at
)
SELECT id, tenant_id, form_key, name, description, version, status,
       schema_json, ui_schema_json, md5(schema_json || ':' || ui_schema_json),
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form_seed
ON CONFLICT (tenant_id, form_key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'PUBLISHED',
    schema_json = EXCLUDED.schema_json,
    ui_schema_json = EXCLUDED.ui_schema_json,
    schema_hash = EXCLUDED.schema_hash,
    published_at = COALESCE(lc_form_definition.published_at, EXCLUDED.published_at),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH fields(field_key, label, field_type, required_flag, placeholder, sort_order) AS (
    VALUES
        ('leave_type', U&'\8BF7\5047\7C7B\578B', 'select', 1, U&'\8BF7\9009\62E9\8BF7\5047\7C7B\578B', 10),
        ('start_date', U&'\5F00\59CB\65E5\671F', 'date', 1, U&'\8BF7\9009\62E9\5F00\59CB\65E5\671F', 20),
        ('end_date', U&'\7ED3\675F\65E5\671F', 'date', 1, U&'\8BF7\9009\62E9\7ED3\675F\65E5\671F', 30),
        ('reason', U&'\8BF7\5047\539F\56E0', 'textarea', 1, U&'\8BF7\8F93\5165\8BF7\5047\539F\56E0', 40),
        ('remark', U&'\5907\6CE8', 'textarea', 0, U&'\8BF7\8F93\5165\5907\6CE8\FF08\9009\586B\FF09', 50)
)
INSERT INTO lc_form_field_definition (
    id, tenant_id, form_definition_id, field_key, label, field_type,
    required_flag, placeholder, sort_order, created_by, created_at, updated_by, updated_at
)
SELECT 'LC_FIELD_LEAVE_' || upper(field_key),
       definition.tenant_id,
       definition.id,
       fields.field_key,
       fields.label,
       fields.field_type,
       fields.required_flag,
       fields.placeholder,
       fields.sort_order,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       'SYSTEM',
       CURRENT_TIMESTAMP
FROM fields
JOIN lc_form_definition definition
  ON definition.tenant_id = 'GLOBAL'
 AND definition.form_key = 'leave'
 AND definition.version = 1
ON CONFLICT (form_definition_id, field_key) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    label = EXCLUDED.label,
    field_type = EXCLUDED.field_type,
    required_flag = EXCLUDED.required_flag,
    placeholder = EXCLUDED.placeholder,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO lc_application (
    id, tenant_id, app_key, name, description, status, latest_version,
    latest_published_version_id, created_by, created_at, updated_by, updated_at
) VALUES (
    'LC_APP_LEAVE',
    'GLOBAL',
    'leave',
    U&'\8BF7\5047\7533\8BF7',
    U&'\652F\6301 AI \69FD\4F4D\62BD\53D6\3001\786E\8BA4\540E\63D0\4EA4\5E76\542F\52A8\5BA1\6279',
    'PUBLISHED',
    1,
    'LC_APPV_LEAVE_001',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, app_key) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'PUBLISHED',
    latest_version = GREATEST(lc_application.latest_version, EXCLUDED.latest_version),
    latest_published_version_id = EXCLUDED.latest_published_version_id,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH form_ref AS (
    SELECT id, form_key, version, schema_hash
    FROM lc_form_definition
    WHERE tenant_id = 'GLOBAL'
      AND form_key = 'leave'
      AND version = 1
      AND status = 'PUBLISHED'
)
INSERT INTO lc_application_version (
    id, tenant_id, application_id, app_key, version, status, name, description,
    primary_form_definition_id, form_key, form_version, schema_hash,
    view_permission_code, metadata_hash, published_at,
    created_by, created_at, updated_by, updated_at
)
SELECT
    'LC_APPV_LEAVE_001',
    'GLOBAL',
    'LC_APP_LEAVE',
    'leave',
    1,
    'PUBLISHED',
    U&'\8BF7\5047\7533\8BF7',
    U&'\8BF7\5047\7533\8BF7\8FD0\884C\65F6\5E94\7528\FF0C\63D0\4EA4\540E\542F\52A8\8BF7\5047\5BA1\6279',
    form_ref.id,
    form_ref.form_key,
    form_ref.version,
    form_ref.schema_hash,
    '/api/v1/lowcode-runtime/apps/leave:GET',
    md5('leave:v1:pages-actions'),
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM form_ref
ON CONFLICT (tenant_id, app_key, version) DO UPDATE SET
    status = 'PUBLISHED',
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    primary_form_definition_id = EXCLUDED.primary_form_definition_id,
    form_key = EXCLUDED.form_key,
    form_version = EXCLUDED.form_version,
    schema_hash = EXCLUDED.schema_hash,
    view_permission_code = EXCLUDED.view_permission_code,
    metadata_hash = EXCLUDED.metadata_hash,
    published_at = COALESCE(lc_application_version.published_at, EXCLUDED.published_at),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH pages(page_type, metadata_json, sort_order) AS (
    VALUES
        ('LIST', '{"columns":[{"fieldKey":"leave_type","label":"\u8bf7\u5047\u7c7b\u578b","width":120},{"fieldKey":"start_date","label":"\u5f00\u59cb\u65e5\u671f","width":140},{"fieldKey":"end_date","label":"\u7ed3\u675f\u65e5\u671f","width":140},{"fieldKey":"reason","label":"\u8bf7\u5047\u539f\u56e0","width":260}],"filters":[{"fieldKey":"leave_type","label":"\u8bf7\u5047\u7c7b\u578b","operator":"eq"}],"rowActions":["OPEN_DETAIL","OPEN_PROCESS"]}', 10),
        ('DETAIL', '{"sections":[{"title":"\u8bf7\u5047\u4fe1\u606f","fields":[{"fieldKey":"leave_type","label":"\u8bf7\u5047\u7c7b\u578b"},{"fieldKey":"start_date","label":"\u5f00\u59cb\u65e5\u671f"},{"fieldKey":"end_date","label":"\u7ed3\u675f\u65e5\u671f"},{"fieldKey":"reason","label":"\u8bf7\u5047\u539f\u56e0"},{"fieldKey":"remark","label":"\u5907\u6ce8"}]}]}', 20),
        ('CREATE', '{"sections":[{"title":"\u586b\u5199\u8bf7\u5047\u5355","fields":[{"fieldKey":"leave_type","label":"\u8bf7\u5047\u7c7b\u578b"},{"fieldKey":"start_date","label":"\u5f00\u59cb\u65e5\u671f"},{"fieldKey":"end_date","label":"\u7ed3\u675f\u65e5\u671f"},{"fieldKey":"reason","label":"\u8bf7\u5047\u539f\u56e0"},{"fieldKey":"remark","label":"\u5907\u6ce8"}]}]}', 30)
)
INSERT INTO lc_application_page (
    id, tenant_id, application_version_id, page_type, metadata_json, sort_order,
    created_by, created_at, updated_by, updated_at
)
SELECT 'LC_APPP_LEAVE_' || page_type,
       'GLOBAL',
       'LC_APPV_LEAVE_001',
       page_type,
       metadata_json,
       sort_order,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       'SYSTEM',
       CURRENT_TIMESTAMP
FROM pages
ON CONFLICT (application_version_id, page_type) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    metadata_json = EXCLUDED.metadata_json,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH form_ref AS (
    SELECT id
    FROM lc_form_definition
    WHERE tenant_id = 'GLOBAL'
      AND form_key = 'leave'
      AND version = 1
      AND status = 'PUBLISHED'
)
INSERT INTO lc_application_action (
    id, tenant_id, application_version_id, action_code, action_type, label,
    permission_code, form_definition_id, process_key, metadata_json, status, sort_order,
    created_by, created_at, updated_by, updated_at
)
SELECT
    'LC_APPA_LEAVE_SUBMIT',
    'GLOBAL',
    'LC_APPV_LEAVE_001',
    'submitAndLaunch',
    'SUBMIT_AND_LAUNCH_WORKFLOW',
    U&'\63D0\4EA4\5E76\542F\52A8\5BA1\6279',
    '/api/v1/lowcode-runtime/apps/leave/actions/submitAndLaunch:POST',
    form_ref.id,
    'leave_request',
    '{"processVersion":1,"launchMode":"EXISTING_DOCUMENT","businessType":"leave"}',
    'ENABLED',
    10,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM form_ref
ON CONFLICT (application_version_id, action_code) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    action_type = EXCLUDED.action_type,
    label = EXCLUDED.label,
    permission_code = EXCLUDED.permission_code,
    form_definition_id = EXCLUDED.form_definition_id,
    process_key = EXCLUDED.process_key,
    metadata_json = EXCLUDED.metadata_json,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH form_ref AS (
    SELECT id, version
    FROM lc_form_definition
    WHERE tenant_id = 'GLOBAL'
      AND form_key = 'leave'
      AND version = 1
)
INSERT INTO wf_process_package(
    id, process_key, name, category, description, version, status,
    process_json, form_schema, form_ui_schema, form_definition_id, form_definition_version,
    published_at, created_by, updated_by
)
SELECT
    'PKG_LEAVE_001',
    'leave_request',
    U&'\8BF7\5047\5BA1\6279',
    'approval',
    U&'\8BF7\5047\7533\8BF7\5355\5BA1\6279\6D41\7A0B',
    1,
    'PUBLISHED',
    $process$
    {
      "version": "1.0.0",
      "processKey": "leave_request",
      "name": "请假审批",
      "category": "approval",
      "form": {
        "schema": {
          "type": "object",
          "additionalProperties": false,
          "required": ["leave_type", "start_date", "end_date", "reason"],
          "properties": {
            "leave_type": {"type": "string", "title": "请假类型", "enum": ["事假", "病假", "年假", "调休", "婚假", "产假"]},
            "start_date": {"type": "string", "format": "date", "title": "开始日期"},
            "end_date": {"type": "string", "format": "date", "title": "结束日期"},
            "reason": {"type": "string", "title": "请假原因", "minLength": 2, "maxLength": 200},
            "remark": {"type": "string", "title": "备注", "maxLength": 300}
          }
        },
        "uiSchema": {
          "leave_type": {"ui:widget": "select"},
          "start_date": {"ui:widget": "date"},
          "end_date": {"ui:widget": "date"},
          "reason": {"ui:widget": "textarea"},
          "remark": {"ui:widget": "textarea"}
        }
      },
      "flow": {
        "nodes": [
          {
            "id": "start",
            "type": "START",
            "name": "开始",
            "next": [{"condition": "true", "target": "manager_approve"}]
          },
          {
            "id": "manager_approve",
            "type": "APPROVAL",
            "name": "直属主管审批",
            "assignment": {"type": "ROLE", "roleCode": "ADMIN"},
            "next": [{"condition": "true", "target": "end"}]
          },
          {"id": "end", "type": "END", "name": "结束"}
        ]
      },
      "permissions": {
        "start": ["ROLE:USER", "ROLE:TENANT_ADMIN", "ROLE:ADMIN"],
        "view": ["ROLE:USER", "ROLE:TENANT_ADMIN", "ROLE:ADMIN"]
      }
    }
    $process$,
    (SELECT schema_json FROM lc_form_definition WHERE id = form_ref.id),
    (SELECT ui_schema_json FROM lc_form_definition WHERE id = form_ref.id),
    form_ref.id,
    form_ref.version,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
FROM form_ref
ON CONFLICT (process_key, version) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    process_json = EXCLUDED.process_json,
    form_schema = EXCLUDED.form_schema,
    form_ui_schema = EXCLUDED.form_ui_schema,
    form_definition_id = EXCLUDED.form_definition_id,
    form_definition_version = EXCLUDED.form_definition_version,
    published_at = EXCLUDED.published_at,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_code, description, created_by, updated_by
) VALUES (
    'M037', 'M030', 'LowcodeLeave', U&'\8BF7\5047\7533\8BF7', '/lowcode/apps/leave',
    '/lowcode/runtime/app', 'mdi:calendar-check-outline', NULL, NULL,
    'menu', 'lowcode', 25, 1, 1, 0, 0,
    0, 0, 0, 0,
    NULL, NULL, NULL, '/api/v1/lowcode-runtime/apps/*:GET',
    U&'\8BF7\5047\7533\8BF7\8FD0\884C\65F6\5165\53E3', 'SYSTEM', 'SYSTEM'
)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    hide_in_menu = EXCLUDED.hide_in_menu,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
) VALUES (
    'AR' || upper(substr(md5('default:/api/v1/lowcode-runtime/apps/*'), 1, 24)),
    'default',
    '/api/v1/lowcode-runtime/apps/*',
    'API_OPERATION',
    'service-lowcode',
    U&'\4F4E\4EE3\7801\8FD0\884C\65F6\5E94\7528\8BFB\53D6',
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
) VALUES (
    'AA' || upper(substr(md5('default:/api/v1/lowcode-runtime/apps/*:GET'), 1, 24)),
    'default',
    '/api/v1/lowcode-runtime/apps/*',
    'GET',
    'API',
    U&'\8BFB\53D6\4F4E\4EE3\7801\8FD0\884C\65F6\5E94\7528',
    1,
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_row.id || ':/api/v1/lowcode-runtime/apps/*:GET'), 1, 24)),
       'default',
       'ROLE',
       role_row.id,
       '/api/v1/lowcode-runtime/apps/*',
       'GET',
       'ALLOW',
       1,
       U&'\8BBF\95EE\4F4E\4EE3\7801\8FD0\884C\65F6\5E94\7528',
       'SYSTEM',
       'SYSTEM'
FROM sys_role role_row
WHERE role_row.role_code IN ('ADMIN', 'TENANT_ADMIN', 'USER')
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH lowcode_resources(resource_code, resource_type, business_object_id, display_name, metadata_json) AS (
    VALUES
        ('LOWCODE_FORM:LEAVE', 'LOWCODE_FORM', 'LC_FORM_LEAVE_001', U&'\8BF7\5047\7533\8BF7', '{"formKey":"leave","version":1}'),
        ('LOWCODE_APP:LEAVE', 'LOWCODE_APP', 'LC_APPV_LEAVE_001', U&'\8BF7\5047\7533\8BF7', '{"appKey":"leave","version":1,"formKey":"leave"}')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, metadata_json, last_synced_at,
    created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       resource_type,
       'service-lowcode',
       business_object_id,
       display_name,
       'ACTIVE',
       0,
       metadata_json,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM lowcode_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    metadata_json = EXCLUDED.metadata_json,
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH actions(resource_code, action_code, action_category, description) AS (
    VALUES
        ('LOWCODE_APP:LEAVE', 'VIEW', 'APPLICATION', U&'\67E5\770B\8BF7\5047\7533\8BF7\5E94\7528'),
        ('LOWCODE_APP:LEAVE', 'DESIGN', 'APPLICATION', U&'\8BBE\8BA1\8BF7\5047\7533\8BF7\5E94\7528'),
        ('LOWCODE_APP:LEAVE', 'PUBLISH', 'APPLICATION', U&'\53D1\5E03\8BF7\5047\7533\8BF7\5E94\7528'),
        ('LOWCODE_APP:LEAVE', 'OFFLINE', 'APPLICATION', U&'\4E0B\7EBF\8BF7\5047\7533\8BF7\5E94\7528'),
        ('LOWCODE_FORM:LEAVE', 'VIEW', 'DOCUMENT', U&'\67E5\770B\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'CREATE', 'DOCUMENT', U&'\65B0\5EFA\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'SAVE', 'DOCUMENT', U&'\4FDD\5B58\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'SUBMIT', 'DOCUMENT', U&'\63D0\4EA4\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'EDIT', 'DOCUMENT', U&'\7F16\8F91\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'DELETE', 'DOCUMENT', U&'\5220\9664\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'APPROVE', 'DOCUMENT', U&'\5BA1\6279\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'REJECT', 'DOCUMENT', U&'\9A73\56DE\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'EXPORT', 'DOCUMENT', U&'\5BFC\51FA\8BF7\5047\7533\8BF7\5355'),
        ('LOWCODE_FORM:LEAVE', 'FIELD_READ', 'FIELD', U&'\8BFB\53D6\8BF7\5047\7533\8BF7\5B57\6BB5'),
        ('LOWCODE_FORM:LEAVE', 'FIELD_WRITE', 'FIELD', U&'\5199\5165\8BF7\5047\7533\8BF7\5B57\6BB5')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:' || resource_code || ':' || action_code), 1, 24)),
       'default',
       resource_code,
       action_code,
       action_category,
       description,
       1,
       'SYSTEM',
       'SYSTEM'
FROM actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH fields(field_key, field_label, field_type) AS (
    VALUES
        ('leave_type', U&'\8BF7\5047\7C7B\578B', 'string'),
        ('start_date', U&'\5F00\59CB\65E5\671F', 'string'),
        ('end_date', U&'\7ED3\675F\65E5\671F', 'string'),
        ('reason', U&'\8BF7\5047\539F\56E0', 'string'),
        ('remark', U&'\5907\6CE8', 'string')
)
INSERT INTO sys_auth_field (
    id, tenant_id, resource_code, field_key, field_label, field_type,
    status, created_by, updated_by
)
SELECT 'AF' || upper(substr(md5('default:LOWCODE_FORM:LEAVE:' || field_key), 1, 24)),
       'default',
       'LOWCODE_FORM:LEAVE',
       field_key,
       field_label,
       field_type,
       1,
       'SYSTEM',
       'SYSTEM'
FROM fields
ON CONFLICT (tenant_id, resource_code, field_key) DO UPDATE SET
    field_label = EXCLUDED.field_label,
    field_type = EXCLUDED.field_type,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH grants(resource_code, action_code, description, exposure) AS (
    VALUES
        ('LOWCODE_APP:LEAVE', 'VIEW', U&'AI \52A9\624B\8BFB\53D6\8BF7\5047\7533\8BF7\5E94\7528', 'STANDARD_USER'),
        ('LOWCODE_FORM:LEAVE', 'VIEW', U&'AI \52A9\624B\8BFB\53D6\8BF7\5047\7533\8BF7\8868\5355', 'STANDARD_USER'),
        ('LOWCODE_FORM:LEAVE', 'CREATE', U&'AI \52A9\624B\521B\5EFA\8BF7\5047\7533\8BF7\8349\7A3F', 'STANDARD_USER'),
        ('LOWCODE_FORM:LEAVE', 'SAVE', U&'AI \52A9\624B\4FDD\5B58\8BF7\5047\7533\8BF7\8349\7A3F', 'STANDARD_USER'),
        ('LOWCODE_FORM:LEAVE', 'SUBMIT', U&'AI \52A9\624B\63D0\4EA4\8BF7\5047\7533\8BF7\5BA1\6279', 'STANDARD_USER'),
        ('LOWCODE_FORM:LEAVE', 'EDIT', U&'AI \52A9\624B\56DE\5199\8BF7\5047\6D41\7A0B\7ED1\5B9A', 'STANDARD_USER')
),
role_actions AS (
    SELECT role_row.id AS role_id,
           grants.resource_code,
           grants.action_code,
           grants.description
    FROM sys_role role_row
    JOIN grants
      ON role_row.role_code = 'ADMIN'
      OR (grants.exposure = 'STANDARD_USER' AND role_row.role_code IN ('TENANT_ADMIN', 'USER'))
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       'default',
       'ROLE',
       role_id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       description,
       'SYSTEM',
       'SYSTEM'
FROM role_actions
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH role_scope(role_code, scope_type) AS (
    VALUES
        ('TENANT_ADMIN', 'ALL'),
        ('USER', 'SELF')
),
actions(action_code) AS (
    VALUES ('VIEW'), ('CREATE'), ('SAVE'), ('SUBMIT'), ('EDIT')
),
policies AS (
    SELECT role_row.id AS role_id,
           role_row.role_code,
           role_scope.scope_type,
           actions.action_code,
           ('DP_LEAVE_' || role_row.role_code || '_' || actions.action_code) AS policy_id,
           ('DPD_LEAVE_' || role_row.role_code || '_' || actions.action_code) AS dimension_id
    FROM sys_role role_row
    JOIN role_scope ON role_scope.role_code = role_row.role_code
    CROSS JOIN actions
)
INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
)
SELECT policy_id,
       'default',
       'ROLE',
       role_id,
       'LOWCODE_FORM:LEAVE',
       action_code,
       'ALLOW',
       'AND',
       1,
       U&'\8BF7\5047\7533\8BF7\8FD0\884C\65F6\6570\636E\8303\56F4',
       'SYSTEM',
       'SYSTEM'
FROM policies
ON CONFLICT (id) DO UPDATE SET
    subject_type = EXCLUDED.subject_type,
    subject_id = EXCLUDED.subject_id,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    effect = EXCLUDED.effect,
    combine_mode = EXCLUDED.combine_mode,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH role_scope(role_code, scope_type) AS (
    VALUES
        ('TENANT_ADMIN', 'ALL'),
        ('USER', 'SELF')
),
actions(action_code) AS (
    VALUES ('VIEW'), ('CREATE'), ('SAVE'), ('SUBMIT'), ('EDIT')
),
dimensions AS (
    SELECT ('DP_LEAVE_' || role_row.role_code || '_' || actions.action_code) AS policy_id,
           ('DPD_LEAVE_' || role_row.role_code || '_' || actions.action_code) AS dimension_id,
           role_scope.scope_type
    FROM sys_role role_row
    JOIN role_scope ON role_scope.role_code = role_row.role_code
    CROSS JOIN actions
)
INSERT INTO sys_data_policy_dimension (
    id, policy_id, dimension_code, scope_type, org_unit_ids, sort_order, created_by, updated_by
)
SELECT dimension_id,
       policy_id,
       'LOWCODE_OWNER',
       scope_type,
       NULL,
       10,
       'SYSTEM',
       'SYSTEM'
FROM dimensions
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id,
    dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type,
    org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
