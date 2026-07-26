-- Register the AI assistant menu and the unified authorization entries it needs.

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_code, description, created_by, updated_by
) VALUES
    ('AI_CENTER', NULL, 'AiCenter', 'AI 能力', '/ai', NULL,
     'mdi:robot-outline', NULL, NULL,
     'catalog', 'ai', 50, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, 'AI 能力中心', 'SYSTEM', 'SYSTEM'),

    ('AI_ASSISTANT', 'AI_CENTER', 'AiAssistantWorkbench', 'AI 助手', '/ai/assistant',
     '/ai/assistant/index', 'mdi:robot-happy-outline', NULL, NULL,
     'menu', 'ai', 10, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, '/ai/assistant:GET', 'Agent 助手入口', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RUN_CREATE', 'AI_ASSISTANT', 'AiAgentRunCreate', '创建 Agent Run', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 10, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/agent/runs:POST', '创建 AI Agent Run', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RUN_READ', 'AI_ASSISTANT', 'AiAgentRunRead', '读取 Agent Run', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 20, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/agent/runs/*:GET', '读取 AI Agent Run 与事件流', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RUN_RESUME', 'AI_ASSISTANT', 'AiAgentRunResume', '恢复 Agent Run', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 30, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/agent/runs/*/resume:POST', '恢复 AI Agent Run', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RUN_CANCEL', 'AI_ASSISTANT', 'AiAgentRunCancel', '取消 Agent Run', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 40, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/agent/runs/*/cancel:POST', '取消 AI Agent Run', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_CHAT', 'AI_ASSISTANT', 'AiLlmChat', 'LLM 对话', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 50, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/ai/chat:POST', 'LLM 网关对话', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_MODEL_READ', 'AI_ASSISTANT', 'AiLlmModelRead', '模型列表', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 60, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/ai/models:GET', 'LLM 模型列表', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RAG_READ', 'AI_ASSISTANT', 'AiRagRead', 'RAG 查询', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 70, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/rag/*:GET', 'RAG 查询', 'SYSTEM', 'SYSTEM'),

    ('AI_BTN_RAG_WRITE', 'AI_ASSISTANT', 'AiRagWrite', 'RAG 写入', NULL, NULL,
     NULL, NULL, NULL,
     'button', 'ai', 80, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/rag/*:POST', 'RAG 写入与装配', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    active_icon = EXCLUDED.active_icon,
    active_path = EXCLUDED.active_path,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    keep_alive = EXCLUDED.keep_alive,
    affix_tab = EXCLUDED.affix_tab,
    hide_in_menu = EXCLUDED.hide_in_menu,
    hide_children_in_menu = EXCLUDED.hide_children_in_menu,
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
    badge = EXCLUDED.badge,
    badge_type = EXCLUDED.badge_type,
    badge_variant = EXCLUDED.badge_variant,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH ai_resources(resource_code, resource_type, owner_service, business_object_id, display_name) AS (
    VALUES
        ('/ai/assistant', 'FRONTEND_ROUTE', 'trio-base-frontend', 'AI_ASSISTANT', 'AI 助手'),
        ('/api/v1/agent/runs', 'API', 'ai-agent-orchestrator', NULL, 'AI Agent Run 创建'),
        ('/api/v1/agent/runs/*', 'API', 'ai-agent-orchestrator', NULL, 'AI Agent Run 读取与事件流'),
        ('/api/v1/agent/runs/*/resume', 'API', 'ai-agent-orchestrator', NULL, 'AI Agent Run 恢复'),
        ('/api/v1/agent/runs/*/cancel', 'API', 'ai-agent-orchestrator', NULL, 'AI Agent Run 取消'),
        ('/api/v1/ai/chat', 'API', 'ai-llm-gateway', NULL, 'LLM 网关对话'),
        ('/api/v1/ai/models', 'API', 'ai-llm-gateway', NULL, 'LLM 模型列表'),
        ('/api/v1/rag/*', 'API', 'ai-rag-service', NULL, 'RAG 查询与写入')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       resource_type,
       owner_service,
       business_object_id,
       display_name,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM ai_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH ai_actions(resource_code, action_code, action_category, description, exposure) AS (
    VALUES
        ('/ai/assistant', 'GET', 'FRONTEND_ROUTE', '访问 AI 助手', 'STANDARD_USER'),
        ('/api/v1/agent/runs', 'POST', 'AGENT_RUNTIME', '创建 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*', 'GET', 'AGENT_RUNTIME', '读取 AI Agent Run 与事件流', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*/resume', 'POST', 'AGENT_RUNTIME', '恢复 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*/cancel', 'POST', 'AGENT_RUNTIME', '取消 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/ai/chat', 'POST', 'LLM_GATEWAY', 'LLM 网关对话', 'ADMIN_ONLY'),
        ('/api/v1/ai/models', 'GET', 'LLM_GATEWAY', 'LLM 模型列表', 'ADMIN_ONLY'),
        ('/api/v1/rag/*', 'GET', 'RAG', 'RAG 查询', 'ADMIN_ONLY'),
        ('/api/v1/rag/*', 'POST', 'RAG', 'RAG 写入与装配', 'ADMIN_ONLY')
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
FROM ai_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH ai_actions(resource_code, action_code, description, exposure) AS (
    VALUES
        ('/ai/assistant', 'GET', '访问 AI 助手', 'STANDARD_USER'),
        ('/api/v1/agent/runs', 'POST', '创建 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*', 'GET', '读取 AI Agent Run 与事件流', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*/resume', 'POST', '恢复 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/agent/runs/*/cancel', 'POST', '取消 AI Agent Run', 'STANDARD_USER'),
        ('/api/v1/ai/chat', 'POST', 'LLM 网关对话', 'ADMIN_ONLY'),
        ('/api/v1/ai/models', 'GET', 'LLM 模型列表', 'ADMIN_ONLY'),
        ('/api/v1/rag/*', 'GET', 'RAG 查询', 'ADMIN_ONLY'),
        ('/api/v1/rag/*', 'POST', 'RAG 写入与装配', 'ADMIN_ONLY')
),
role_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN ai_actions action
      ON role.role_code = 'ADMIN'
      OR (action.exposure = 'STANDARD_USER' AND role.role_code IN ('TENANT_ADMIN', 'USER'))
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

WITH expense_actions(resource_code, action_code, description, exposure) AS (
    VALUES
        ('LOWCODE_APP:EXPENSE_REPORT', 'VIEW', 'AI 助手读取费用报销应用', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'VIEW', 'AI 助手读取费用报销表单', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'CREATE', 'AI 助手创建费用报销草稿', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'SUBMIT', 'AI 助手提交费用报销审批', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'SAVE', 'AI 助手保存费用报销草稿', 'STANDARD_USER')
),
normalized_expense_actions AS (
    SELECT resource_code, action_code, 'DOCUMENT' AS action_category, description
    FROM expense_actions
    WHERE resource_code = 'LOWCODE_FORM:EXPENSE'
    UNION ALL
    SELECT resource_code, action_code, 'APPLICATION' AS action_category, description
    FROM expense_actions
    WHERE resource_code = 'LOWCODE_APP:EXPENSE_REPORT'
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
FROM normalized_expense_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH expense_actions(resource_code, action_code, description, exposure) AS (
    VALUES
        ('LOWCODE_APP:EXPENSE_REPORT', 'VIEW', 'AI 助手读取费用报销应用', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'VIEW', 'AI 助手读取费用报销表单', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'CREATE', 'AI 助手创建费用报销草稿', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'SUBMIT', 'AI 助手提交费用报销审批', 'STANDARD_USER'),
        ('LOWCODE_FORM:EXPENSE', 'SAVE', 'AI 助手保存费用报销草稿', 'STANDARD_USER')
),
role_expense_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN expense_actions action
      ON role.role_code = 'ADMIN'
      OR (action.exposure = 'STANDARD_USER' AND role.role_code IN ('TENANT_ADMIN', 'USER'))
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
FROM role_expense_actions
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
