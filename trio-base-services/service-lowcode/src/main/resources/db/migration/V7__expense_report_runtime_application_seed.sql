-- Published expense-report rapid application seed owned by service-lowcode.
-- This backfills the pilot expense form into the generic runtime without
-- depending on auth or workflow-engine migrations.

WITH form_seed AS (
    SELECT
        'LC_FORM_EXPENSE_001'::varchar AS id,
        'GLOBAL'::varchar AS tenant_id,
        'expense'::varchar AS form_key,
        U&'\8D39\7528\62A5\9500'::varchar AS name,
        U&'\8D39\7528\62A5\9500\901A\7528\5FEB\901F\5E94\7528\8868\5355'::varchar AS description,
        1::integer AS version,
        'PUBLISHED'::varchar AS status,
        '{"additionalProperties":false,"properties":{"amount":{"minimum":0.01,"title":"\u91d1\u989d","type":"number"},"reason":{"maxLength":200,"minLength":2,"title":"\u4e8b\u7531","type":"string"},"dept":{"minLength":1,"title":"\u6240\u5c5e\u90e8\u95e8","type":"string"},"remark":{"maxLength":300,"title":"\u5907\u6ce8","type":"string"}},"required":["amount","reason","dept"],"title":"\u8d39\u7528\u62a5\u9500","type":"object"}'::text AS schema_json,
        '{"amount":{"ui:placeholder":"\u8bf7\u8f93\u5165\u62a5\u9500\u91d1\u989d","ui:widget":"money"},"reason":{"ui:placeholder":"\u8bf7\u8f93\u5165\u62a5\u9500\u4e8b\u7531","ui:widget":"textarea"},"dept":{"ui:placeholder":"\u8bf7\u8f93\u5165\u6240\u5c5e\u90e8\u95e8","ui:widget":"string"},"remark":{"ui:placeholder":"\u8bf7\u8f93\u5165\u5907\u6ce8\uff08\u9009\u586b\uff09","ui:widget":"textarea"}}'::text AS ui_schema_json
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
        ('amount', U&'\91D1\989D', 'money', 1, U&'\8BF7\8F93\5165\62A5\9500\91D1\989D', 10),
        ('reason', U&'\4E8B\7531', 'textarea', 1, U&'\8BF7\8F93\5165\62A5\9500\4E8B\7531', 20),
        ('dept', U&'\6240\5C5E\90E8\95E8', 'string', 1, U&'\8BF7\8F93\5165\6240\5C5E\90E8\95E8', 30),
        ('remark', U&'\5907\6CE8', 'textarea', 0, U&'\8BF7\8F93\5165\5907\6CE8\FF08\9009\586B\FF09', 40)
)
INSERT INTO lc_form_field_definition (
    id, tenant_id, form_definition_id, field_key, label, field_type,
    required_flag, placeholder, sort_order, created_by, created_at, updated_by, updated_at
)
SELECT 'LC_FIELD_EXPENSE_' || upper(field_key),
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
 AND definition.form_key = 'expense'
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
    'LC_APP_EXPENSE_REPORT',
    'GLOBAL',
    'expense_report',
    U&'\8D39\7528\62A5\9500',
    U&'\57FA\4E8E\901A\7528\8FD0\884C\65F6\7684\8D39\7528\62A5\9500\5FEB\901F\5E94\7528',
    'PUBLISHED',
    1,
    'LC_APPV_EXPENSE_REPORT_001',
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
      AND form_key = 'expense'
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
    'LC_APPV_EXPENSE_REPORT_001',
    'GLOBAL',
    'LC_APP_EXPENSE_REPORT',
    'expense_report',
    1,
    'PUBLISHED',
    U&'\8D39\7528\62A5\9500',
    U&'\8D39\7528\62A5\9500\901A\7528\5FEB\901F\5E94\7528\FF0C\63D0\4EA4\540E\53EF\542F\52A8\5BA1\6279\6D41\7A0B',
    form_ref.id,
    form_ref.form_key,
    form_ref.version,
    form_ref.schema_hash,
    '/api/v1/forms/expense/instances:GET',
    md5('expense_report:v1:pages-actions'),
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
        ('LIST', '{"columns":[{"fieldKey":"amount","label":"\u62a5\u9500\u91d1\u989d","format":"money","width":130},{"fieldKey":"reason","label":"\u62a5\u9500\u4e8b\u7531","width":260},{"fieldKey":"dept","label":"\u6240\u5c5e\u90e8\u95e8","width":150}]}', 10),
        ('DETAIL', '{"sections":[{"title":"\u5355\u636e\u4fe1\u606f","fields":[{"fieldKey":"amount","label":"\u62a5\u9500\u91d1\u989d","format":"money"},{"fieldKey":"reason","label":"\u62a5\u9500\u4e8b\u7531"},{"fieldKey":"dept","label":"\u6240\u5c5e\u90e8\u95e8"},{"fieldKey":"remark","label":"\u5907\u6ce8"}]}]}', 20),
        ('CREATE', '{"sections":[{"title":"\u586b\u5199\u62a5\u9500","fields":[{"fieldKey":"amount","label":"\u62a5\u9500\u91d1\u989d","format":"money"},{"fieldKey":"reason","label":"\u62a5\u9500\u4e8b\u7531"},{"fieldKey":"dept","label":"\u6240\u5c5e\u90e8\u95e8"},{"fieldKey":"remark","label":"\u5907\u6ce8"}]}]}', 30)
)
INSERT INTO lc_application_page (
    id, tenant_id, application_version_id, page_type, metadata_json, sort_order,
    created_by, created_at, updated_by, updated_at
)
SELECT 'LC_APPP_EXPENSE_' || page_type,
       'GLOBAL',
       'LC_APPV_EXPENSE_REPORT_001',
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
      AND form_key = 'expense'
      AND version = 1
      AND status = 'PUBLISHED'
)
INSERT INTO lc_application_action (
    id, tenant_id, application_version_id, action_code, action_type, label,
    permission_code, form_definition_id, process_key, metadata_json, status, sort_order,
    created_by, created_at, updated_by, updated_at
)
SELECT
    'LC_APPA_EXPENSE_SUBMIT',
    'GLOBAL',
    'LC_APPV_EXPENSE_REPORT_001',
    'submitAndLaunch',
    'SUBMIT_AND_LAUNCH_WORKFLOW',
    U&'\63D0\4EA4\5E76\542F\52A8\5BA1\6279',
    '/api/v1/forms/expense/submit:POST',
    form_ref.id,
    'expense_report',
    '{"processVersion":1,"launchMode":"EXISTING_DOCUMENT","businessType":"expense_report"}',
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
