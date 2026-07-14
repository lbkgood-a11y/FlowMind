-- V1__workflow_engine_schema.sql — 工作流引擎初始 Schema
-- 流程包定义、流程实例、待办任务、节点执行记录
-- 基于 Temporal 运行时底座

-- ============================================================
-- 1. 流程包定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_process_package (
    id              VARCHAR(32)  PRIMARY KEY,
    process_key     VARCHAR(128) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    category        VARCHAR(32)  NOT NULL DEFAULT 'approval'
                    CHECK (category IN ('approval', 'business', 'integration')),
    description     VARCHAR(512),
    version         INTEGER      NOT NULL DEFAULT 1,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    process_json    TEXT         NOT NULL,         -- 完整流程包 JSON
    form_schema     TEXT,                          -- 抽取的表单 JSON Schema，方便前端不用解析整个包
    form_ui_schema  TEXT,                          -- 抽取的 UI Schema
    created_by      VARCHAR(32),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_package_key ON wf_process_package(process_key);

-- ============================================================
-- 2. 流程实例表
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_process_instance (
    id                  VARCHAR(32)  PRIMARY KEY,
    process_package_id  VARCHAR(32)  NOT NULL REFERENCES wf_process_package(id),
    process_key         VARCHAR(128) NOT NULL,
    process_name        VARCHAR(256) NOT NULL,
    version             INTEGER      NOT NULL,      -- 运行时使用的流程包版本
    title               VARCHAR(256),                -- 实例标题，如 "张三的费用报销-2024-01"
    status              VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
                        CHECK (status IN ('RUNNING', 'SUSPENDED', 'COMPLETED', 'TERMINATED')),
    form_data           TEXT,                        -- 提交的表单数据 JSON
    initiator_id        VARCHAR(32)  NOT NULL,
    initiator_name      VARCHAR(64)  NOT NULL,
    current_node_id     VARCHAR(64),                 -- 当前所在的节点 ID
    workflow_id         VARCHAR(256),                -- Temporal Workflow ID
    run_id              VARCHAR(256),                -- Temporal Run ID
    started_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_instance_key ON wf_process_instance(process_key);
CREATE INDEX IF NOT EXISTS idx_wf_instance_initiator ON wf_process_instance(initiator_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_status ON wf_process_instance(status);

-- ============================================================
-- 3. 待办任务表（有人参与的节点产生）
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_task (
    id                  VARCHAR(32)  PRIMARY KEY,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id),
    process_key         VARCHAR(128) NOT NULL,
    process_name        VARCHAR(256) NOT NULL,
    node_id             VARCHAR(64)  NOT NULL,       -- 流程定义中的节点 ID
    node_name           VARCHAR(128) NOT NULL,       -- 节点显示名称
    node_type           VARCHAR(32)  NOT NULL,       -- APPROVAL / COUNTERSIGN / NOTIFY
    title               VARCHAR(256),                -- 任务标题
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'TRANSFERRED', 'CANCELLED', 'AUTO_SKIPPED')),
    assignee_id         VARCHAR(32),                 -- 固化参与者快照：用户 ID
    assignee_name       VARCHAR(64),                 -- 固化参与者快照：用户名
    assignee_type       VARCHAR(16)  NOT NULL        -- 解析方式快照：ROLE/DEPT/USER/SYSTEM
                        CHECK (assignee_type IN ('ROLE', 'DEPT', 'USER', 'SYSTEM')),
    comment             TEXT,                        -- 审批意见
    claimed_at          TIMESTAMP,                   -- 签收时间（用户点击"办理"）
    completed_at        TIMESTAMP,                   -- 完成时间
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_task_instance ON wf_task(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_assignee ON wf_task(assignee_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_status ON wf_task(status);

-- ============================================================
-- 4. 节点执行记录表（审计/历史追溯）
-- ============================================================
CREATE TABLE IF NOT EXISTS wf_node_record (
    id                  VARCHAR(32)  PRIMARY KEY,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id),
    node_id             VARCHAR(64)  NOT NULL,
    node_name           VARCHAR(128) NOT NULL,
    node_type           VARCHAR(32)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'FAILED', 'SKIPPED')),
    assignee_snapshot   TEXT,                        -- 参与者快照 JSON
    result              TEXT,                        -- 执行结果 JSON（条件计算结果/审批结果等）
    entered_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exited_at           TIMESTAMP,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_node_instance ON wf_node_record(process_instance_id);

-- ============================================================
-- 种子数据：演示用费用报销流程包
-- ============================================================
INSERT INTO wf_process_package(id, process_key, name, category, description, version, status, process_json) VALUES
('PKG001', 'expense_report', '费用报销', 'approval', '员工费用报销审批流程', 1, 'PUBLISHED',
'{
  "version": "1.0.0",
  "processKey": "expense_report",
  "name": "费用报销",
  "category": "approval",
  "form": {
    "schema": {
      "type": "object",
      "required": ["amount", "reason", "dept"],
      "properties": {
        "amount": {"type": "number", "title": "报销金额", "minimum": 0.01},
        "reason": {"type": "string", "title": "报销事由", "minLength": 2},
        "dept": {"type": "string", "title": "所属部门"},
        "remark": {"type": "string", "title": "备注"}
      }
    },
    "uiSchema": {
      "amount": {"ui:widget": "money"},
      "reason": {"ui:widget": "textarea"},
      "remark": {"ui:widget": "textarea"}
    }
  },
  "flow": {
    "nodes": [
      {"id": "start", "type": "START", "name": "开始", "next": [{"condition": "true", "target": "dept_approve"}]},
      {"id": "dept_approve", "type": "APPROVAL", "name": "部门审批",
        "assignment": {"type": "ROLE", "roleCode": "DEPT_HEAD"},
        "next": [
          {"condition": "amount > 5000", "target": "finance_approve"},
          {"condition": "true", "target": "end"}
        ]},
      {"id": "finance_approve", "type": "APPROVAL", "name": "财务审批",
        "assignment": {"type": "ROLE", "roleCode": "FINANCE"},
        "next": [{"condition": "true", "target": "end"}]},
      {"id": "end", "type": "END", "name": "结束"}
    ]
  },
  "permissions": {
    "start": ["ROLE:USER"],
    "view": ["ROLE:USER", "ROLE:DEPT_HEAD", "ROLE:FINANCE"]
  },
  "extends": {
    "notifications": [
      {"on": "node_enter", "to": "assignee", "channel": "internal"}
    ]
  }
}')
ON CONFLICT (id) DO NOTHING;
