-- Business object catalog and process business-closure runtime foundation.
-- Database rows register selectable business metadata only; side effects must
-- resolve through code-registered executors via executor_key.

-- ============================================================
-- 1. Business object catalog
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_biz_object (
    id               VARCHAR(32)  PRIMARY KEY,
    tenant_id        VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    type_code        VARCHAR(128) NOT NULL,
    display_name     VARCHAR(128) NOT NULL,
    service_code     VARCHAR(128) NOT NULL,
    description      VARCHAR(512),
    version          INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    source_object_id VARCHAR(32) REFERENCES wf_biz_object(id) ON DELETE SET NULL,
    metadata_json    TEXT,
    published_at     TIMESTAMP,
    offline_at       TIMESTAMP,
    created_by       VARCHAR(32),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(32),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_object_tenant_type_version
    ON wf_biz_object(tenant_id, type_code, version);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_object_single_draft
    ON wf_biz_object(tenant_id, type_code)
    WHERE status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_wf_biz_object_effective
    ON wf_biz_object(tenant_id, type_code, status);

CREATE INDEX IF NOT EXISTS idx_wf_biz_object_source
    ON wf_biz_object(source_object_id);

-- ============================================================
-- 2. Catalog child tables
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_biz_object_status (
    id            VARCHAR(32)  PRIMARY KEY,
    object_id     VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    status_code   VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    status_group  VARCHAR(32),
    is_initial    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    metadata_json TEXT,
    created_by    VARCHAR(32),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_status_code
    ON wf_biz_object_status(object_id, status_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_status_object
    ON wf_biz_object_status(object_id, sort_order);

CREATE TABLE IF NOT EXISTS wf_biz_object_form (
    id                 VARCHAR(32)  PRIMARY KEY,
    object_id          VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    form_role          VARCHAR(32)  NOT NULL
                       CHECK (form_role IN ('START', 'APPROVAL_VIEW', 'SUPPLEMENT', 'DETAIL')),
    display_name       VARCHAR(128) NOT NULL,
    form_definition_id VARCHAR(32),
    form_key           VARCHAR(128) NOT NULL,
    form_version       INTEGER,
    required           BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    metadata_json      TEXT,
    created_by         VARCHAR(32),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(32),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_form_role_key
    ON wf_biz_object_form(object_id, form_role, form_key);
CREATE INDEX IF NOT EXISTS idx_wf_biz_form_object
    ON wf_biz_object_form(object_id, sort_order);

CREATE TABLE IF NOT EXISTS wf_biz_object_permission (
    id              VARCHAR(32)  PRIMARY KEY,
    object_id       VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    action_code     VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    permission_code VARCHAR(256) NOT NULL,
    action_group    VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS',
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    metadata_json   TEXT,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_permission_action
    ON wf_biz_object_permission(object_id, action_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_permission_object
    ON wf_biz_object_permission(object_id, sort_order);

CREATE TABLE IF NOT EXISTS wf_biz_object_action (
    id                VARCHAR(32)  PRIMARY KEY,
    object_id         VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    action_code       VARCHAR(64)  NOT NULL,
    display_name      VARCHAR(128) NOT NULL,
    action_type       VARCHAR(32)  NOT NULL
                      CHECK (action_type IN ('CREATE_DOCUMENT', 'UPDATE_STATUS', 'DOMAIN_EVENT', 'NOTIFICATION', 'BUSINESS_EFFECT')),
    executor_key      VARCHAR(128) NOT NULL,
    mode_default      VARCHAR(16)  NOT NULL DEFAULT 'ASYNC'
                      CHECK (mode_default IN ('HARD', 'ASYNC', 'SOFT')),
    permission_action VARCHAR(64),
    param_schema_json TEXT,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    metadata_json     TEXT,
    created_by        VARCHAR(32),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(32),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_action_code
    ON wf_biz_object_action(object_id, action_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_action_object
    ON wf_biz_object_action(object_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_wf_biz_action_executor
    ON wf_biz_object_action(executor_key);

CREATE TABLE IF NOT EXISTS wf_biz_object_event (
    id                VARCHAR(32)  PRIMARY KEY,
    object_id         VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    event_code        VARCHAR(128) NOT NULL,
    display_name      VARCHAR(128) NOT NULL,
    event_type        VARCHAR(128) NOT NULL,
    payload_schema_json TEXT,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    metadata_json     TEXT,
    created_by        VARCHAR(32),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(32),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_event_code
    ON wf_biz_object_event(object_id, event_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_event_object
    ON wf_biz_object_event(object_id, sort_order);

CREATE TABLE IF NOT EXISTS wf_biz_object_agent_action (
    id                VARCHAR(32)  PRIMARY KEY,
    object_id         VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    agent_action_code VARCHAR(64)  NOT NULL,
    display_name      VARCHAR(128) NOT NULL,
    executor_key      VARCHAR(128) NOT NULL,
    permission_action VARCHAR(64),
    param_schema_json TEXT,
    result_schema_json TEXT,
    mode_default      VARCHAR(16)  NOT NULL DEFAULT 'ASYNC'
                      CHECK (mode_default IN ('HARD', 'ASYNC', 'SOFT')),
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    metadata_json     TEXT,
    created_by        VARCHAR(32),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(32),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_agent_action_code
    ON wf_biz_object_agent_action(object_id, agent_action_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_agent_action_object
    ON wf_biz_object_agent_action(object_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_wf_biz_agent_action_executor
    ON wf_biz_object_agent_action(executor_key);

CREATE TABLE IF NOT EXISTS wf_biz_object_template (
    id            VARCHAR(32)  PRIMARY KEY,
    object_id     VARCHAR(32)  NOT NULL REFERENCES wf_biz_object(id) ON DELETE CASCADE,
    template_code VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    template_type VARCHAR(32)  NOT NULL
                  CHECK (template_type IN ('LAUNCH', 'CLOSURE', 'PROCESS', 'VISUALIZATION')),
    config_json   TEXT         NOT NULL,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_by    VARCHAR(32),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_biz_template_code
    ON wf_biz_object_template(object_id, template_code);
CREATE INDEX IF NOT EXISTS idx_wf_biz_template_object
    ON wf_biz_object_template(object_id, sort_order);

-- ============================================================
-- 3. Outcome and closure runtime records
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_process_outcome (
    id                  VARCHAR(32)  PRIMARY KEY,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id) ON DELETE CASCADE,
    outcome_version     INTEGER      NOT NULL DEFAULT 1,
    process_package_id  VARCHAR(32)  NOT NULL REFERENCES wf_process_package(id),
    process_key         VARCHAR(128) NOT NULL,
    process_version     INTEGER      NOT NULL,
    business_type       VARCHAR(128),
    business_id         VARCHAR(128),
    outcome_status      VARCHAR(32)  NOT NULL
                        CHECK (outcome_status IN ('APPROVED', 'REJECTED', 'TERMINATED', 'SUSPENDED', 'CLOSURE_FAILED')),
    result_code         VARCHAR(64),
    reason              TEXT,
    tenant_id           VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    initiator_id        VARCHAR(32),
    last_operator_id    VARCHAR(32),
    trace_id            VARCHAR(64),
    payload_json        TEXT,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_process_outcome_instance_version
    ON wf_process_outcome(process_instance_id, outcome_version);
CREATE INDEX IF NOT EXISTS idx_wf_process_outcome_business
    ON wf_process_outcome(business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_wf_process_outcome_status
    ON wf_process_outcome(outcome_status, created_at);

CREATE TABLE IF NOT EXISTS wf_process_closure (
    id                  VARCHAR(32)  PRIMARY KEY,
    outcome_id          VARCHAR(32)  NOT NULL REFERENCES wf_process_outcome(id) ON DELETE CASCADE,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id) ON DELETE CASCADE,
    business_type       VARCHAR(128),
    business_id         VARCHAR(128),
    closure_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
                        CHECK (closure_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'COMPENSATING', 'SKIPPED')),
    hard_failure_policy VARCHAR(32),
    trace_id            VARCHAR(64),
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_process_closure_outcome
    ON wf_process_closure(outcome_id);
CREATE INDEX IF NOT EXISTS idx_wf_process_closure_instance
    ON wf_process_closure(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_process_closure_status
    ON wf_process_closure(closure_status, created_at);

CREATE TABLE IF NOT EXISTS wf_closure_effect (
    id                  VARCHAR(32)  PRIMARY KEY,
    closure_id          VARCHAR(32)  NOT NULL REFERENCES wf_process_closure(id) ON DELETE CASCADE,
    effect_key          VARCHAR(128) NOT NULL,
    effect_type         VARCHAR(32)  NOT NULL
                        CHECK (effect_type IN ('CREATE_DOCUMENT', 'BUSINESS_STATUS_UPDATE', 'DOMAIN_EVENT', 'NOTIFICATION', 'AGENT_FOLLOW_UP', 'MANUAL')),
    trigger_outcome     VARCHAR(32),
    business_action_code VARCHAR(64),
    executor_key        VARCHAR(128),
    mode                VARCHAR(16)  NOT NULL DEFAULT 'ASYNC'
                        CHECK (mode IN ('HARD', 'ASYNC', 'SOFT')),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYING', 'SKIPPED', 'MANUALLY_HANDLED')),
    idempotency_key     VARCHAR(256) NOT NULL,
    request_json        TEXT,
    result_json         TEXT,
    failure_category    VARCHAR(64),
    last_error          TEXT,
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP,
    operator_id         VARCHAR(32),
    trace_id            VARCHAR(64),
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_closure_effect_key
    ON wf_closure_effect(closure_id, effect_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_closure_effect_idempotency
    ON wf_closure_effect(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_wf_closure_effect_status
    ON wf_closure_effect(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_wf_closure_effect_executor
    ON wf_closure_effect(executor_key);

CREATE TABLE IF NOT EXISTS wf_closure_outbox (
    id            VARCHAR(32)  PRIMARY KEY,
    closure_id    VARCHAR(32)  NOT NULL REFERENCES wf_process_closure(id) ON DELETE CASCADE,
    effect_id     VARCHAR(32)  REFERENCES wf_closure_effect(id) ON DELETE CASCADE,
    event_type    VARCHAR(128) NOT NULL,
    payload_json  TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYING', 'SKIPPED')),
    attempt_count INTEGER      NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    locked_at     TIMESTAMP,
    published_at  TIMESTAMP,
    last_error    TEXT,
    trace_id      VARCHAR(64),
    created_by    VARCHAR(32),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_closure_outbox_status
    ON wf_closure_outbox(status, next_retry_at, created_at);
CREATE INDEX IF NOT EXISTS idx_wf_closure_outbox_effect
    ON wf_closure_outbox(effect_id);

-- ============================================================
-- 4. Global expense-report catalog seed
-- ============================================================
INSERT INTO wf_biz_object(
    id, tenant_id, type_code, display_name, service_code, description,
    version, status, metadata_json, published_at, created_by, updated_by
) VALUES (
    'BIZ_EXPENSE_REPORT',
    'GLOBAL',
    'expense_report',
    '报销单',
    'expense-service',
    '费用报销业务对象目录，用于流程全闭环 MVP',
    1,
    'PUBLISHED',
    '{"primaryField":"expenseReportId","titleFields":["reason","amount"]}',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (tenant_id, type_code, version) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    service_code = EXCLUDED.service_code,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    metadata_json = EXCLUDED.metadata_json,
    published_at = EXCLUDED.published_at,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_status(
    id, object_id, status_code, display_name, status_group, is_initial, is_terminal, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_STATUS_DRAFT', 'BIZ_EXPENSE_REPORT', 'DRAFT', '草稿', 'INITIAL', TRUE, FALSE, 10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_STATUS_INAPPROVAL', 'BIZ_EXPENSE_REPORT', 'IN_APPROVAL', '审批中', 'RUNNING', FALSE, FALSE, 20, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_STATUS_APPROVED', 'BIZ_EXPENSE_REPORT', 'APPROVED', '已通过', 'SUCCESS', FALSE, TRUE, 30, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_STATUS_REJECTED', 'BIZ_EXPENSE_REPORT', 'REJECTED', '已驳回', 'FAILURE', FALSE, TRUE, 40, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, status_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    status_group = EXCLUDED.status_group,
    is_initial = EXCLUDED.is_initial,
    is_terminal = EXCLUDED.is_terminal,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_form(
    id, object_id, form_role, display_name, form_definition_id, form_key, form_version, required, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_FORM_START', 'BIZ_EXPENSE_REPORT', 'START', '报销申请表', NULL, 'expense_report_start', 1, TRUE, 10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_FORM_DETAIL', 'BIZ_EXPENSE_REPORT', 'DETAIL', '报销详情表', NULL, 'expense_report_detail', 1, FALSE, 20, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, form_role, form_key) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    form_definition_id = EXCLUDED.form_definition_id,
    form_version = EXCLUDED.form_version,
    required = EXCLUDED.required,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_permission(
    id, object_id, action_code, display_name, permission_code, action_group, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_PERM_SUBMIT', 'BIZ_EXPENSE_REPORT', 'submit', '提交报销', '/api/v1/process-instances/start:POST', 'LAUNCH', 10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_PERM_VIEW', 'BIZ_EXPENSE_REPORT', 'view', '查看报销', '/api/v1/process-instances:GET', 'VIEW', 20, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_PERM_APPROVE', 'BIZ_EXPENSE_REPORT', 'approve', '审批报销', '/api/v1/tasks/*/approve:POST', 'TASK', 30, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_PERM_RETRY', 'BIZ_EXPENSE_REPORT', 'retryClosure', '重试闭环', '/api/v1/process-closures/*/retry:POST', 'CLOSURE', 40, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_PERM_AGENT', 'BIZ_EXPENSE_REPORT', 'agentFollowUp', 'Agent 跟进', '/api/v1/process-agent-follow-up/*:POST', 'AGENT', 50, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, action_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    permission_code = EXCLUDED.permission_code,
    action_group = EXCLUDED.action_group,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_action(
    id, object_id, action_code, display_name, action_type, executor_key,
    mode_default, permission_action, param_schema_json, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_ACT_CREATE', 'BIZ_EXPENSE_REPORT', 'createDocument', '创建报销单', 'CREATE_DOCUMENT',
     'expense_report.createDocument', 'HARD', 'submit',
     '{"type":"object","required":["amount","reason"],"properties":{"amount":{"type":"number","title":"报销金额"},"reason":{"type":"string","title":"报销事由"},"dept":{"type":"string","title":"所属部门"},"remark":{"type":"string","title":"备注"}}}',
     10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_ACT_UPDATE_STATUS', 'BIZ_EXPENSE_REPORT', 'updateStatus', '更新报销单状态', 'UPDATE_STATUS',
     'expense_report.updateStatus', 'HARD', 'submit',
     '{"type":"object","required":["status"],"properties":{"status":{"type":"string","title":"目标状态","enum":["IN_APPROVAL","APPROVED","REJECTED"]}}}',
     20, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_ACT_NOTIFY', 'BIZ_EXPENSE_REPORT', 'notifyApplicant', '通知申请人', 'NOTIFICATION',
     'platform.notification.send', 'ASYNC', 'view',
     '{"type":"object","required":["recipient"],"properties":{"recipient":{"type":"string","title":"通知对象","enum":["INITIATOR"]},"channel":{"type":"string","title":"通知渠道","enum":["INTERNAL"]}}}',
     30, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, action_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    action_type = EXCLUDED.action_type,
    executor_key = EXCLUDED.executor_key,
    mode_default = EXCLUDED.mode_default,
    permission_action = EXCLUDED.permission_action,
    param_schema_json = EXCLUDED.param_schema_json,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_event(
    id, object_id, event_code, display_name, event_type, payload_schema_json, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_EVT_APPROVED', 'BIZ_EXPENSE_REPORT', 'ExpenseReportApproved', '报销审批通过', 'ExpenseReportApproved',
     '{"type":"object","required":["businessId","amount"],"properties":{"businessId":{"type":"string"},"amount":{"type":"number"},"initiatorId":{"type":"string"}}}',
     10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_EVT_REJECTED', 'BIZ_EXPENSE_REPORT', 'ExpenseReportRejected', '报销审批驳回', 'ExpenseReportRejected',
     '{"type":"object","required":["businessId"],"properties":{"businessId":{"type":"string"},"reason":{"type":"string"},"initiatorId":{"type":"string"}}}',
     20, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, event_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    event_type = EXCLUDED.event_type,
    payload_schema_json = EXCLUDED.payload_schema_json,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_agent_action(
    id, object_id, agent_action_code, display_name, executor_key, permission_action,
    param_schema_json, result_schema_json, mode_default, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_AGENT_RISK', 'BIZ_EXPENSE_REPORT', 'riskCheck', '报销风险检查', 'expense_report.agent.riskCheck', 'agentFollowUp',
     '{"type":"object","required":["amount"],"properties":{"amount":{"type":"number","title":"报销金额"},"reason":{"type":"string","title":"报销事由"}}}',
     '{"type":"object","properties":{"riskLevel":{"type":"string"},"summary":{"type":"string"}}}',
     'ASYNC', 10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_AGENT_PAYMENT', 'BIZ_EXPENSE_REPORT', 'paymentSummary', '付款准备摘要', 'expense_report.agent.paymentSummary', 'agentFollowUp',
     '{"type":"object","required":["amount","businessId"],"properties":{"businessId":{"type":"string","title":"报销单编号"},"amount":{"type":"number","title":"报销金额"}}}',
     '{"type":"object","properties":{"summary":{"type":"string"},"nextAction":{"type":"string"}}}',
     'ASYNC', 20, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, agent_action_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    executor_key = EXCLUDED.executor_key,
    permission_action = EXCLUDED.permission_action,
    param_schema_json = EXCLUDED.param_schema_json,
    result_schema_json = EXCLUDED.result_schema_json,
    mode_default = EXCLUDED.mode_default,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wf_biz_object_template(
    id, object_id, template_code, display_name, template_type, config_json, sort_order, created_by, updated_by
) VALUES
    ('BIZ_EXP_TPL_LAUNCH', 'BIZ_EXPENSE_REPORT', 'defaultLaunch', '默认报销发起策略', 'LAUNCH',
     '{"modes":["EXISTING_DOCUMENT","CREATE_AND_LAUNCH"],"allowedStatuses":["DRAFT","REJECTED"],"startEffects":[{"actionCode":"updateStatus","mode":"HARD","params":{"status":"IN_APPROVAL"}}]}',
     10, 'SYSTEM', 'SYSTEM'),
    ('BIZ_EXP_TPL_CLOSURE', 'BIZ_EXPENSE_REPORT', 'defaultClosure', '默认报销闭环策略', 'CLOSURE',
     '{"outcomes":{"APPROVED":[{"actionCode":"updateStatus","mode":"HARD","params":{"status":"APPROVED"}},{"eventCode":"ExpenseReportApproved","mode":"ASYNC"},{"actionCode":"notifyApplicant","mode":"ASYNC"},{"agentActionCode":"paymentSummary","mode":"ASYNC"}],"REJECTED":[{"actionCode":"updateStatus","mode":"HARD","params":{"status":"REJECTED"}},{"eventCode":"ExpenseReportRejected","mode":"ASYNC"},{"actionCode":"notifyApplicant","mode":"ASYNC"}]}}',
     20, 'SYSTEM', 'SYSTEM')
ON CONFLICT (object_id, template_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    template_type = EXCLUDED.template_type,
    config_json = EXCLUDED.config_json,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
