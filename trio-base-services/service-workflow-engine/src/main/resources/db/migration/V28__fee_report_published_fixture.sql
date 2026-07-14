-- Complete immutable version-1 snapshots for the local expense-report acceptance flow.

INSERT INTO wf_process_package(
    id, process_key, name, category, description, version, status,
    process_json, form_schema, form_ui_schema, published_at,
    created_by, updated_by
) VALUES (
    'PKG001',
    'expense_report',
    '费用报销',
    'approval',
    '员工费用报销审批流程',
    1,
    'PUBLISHED',
    $process$
    {
      "version": "1.0.0",
      "processKey": "expense_report",
      "name": "费用报销",
      "category": "approval",
      "form": {
        "schema": {
          "type": "object",
          "additionalProperties": false,
          "required": ["amount", "reason", "dept"],
          "properties": {
            "amount": {"type": "number", "title": "报销金额", "minimum": 0.01},
            "reason": {"type": "string", "title": "报销事由", "minLength": 2},
            "dept": {"type": "string", "title": "所属部门", "minLength": 1},
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
          {
            "id": "start",
            "type": "START",
            "name": "开始",
            "next": [{"condition": "true", "target": "dept_approve"}]
          },
          {
            "id": "dept_approve",
            "type": "APPROVAL",
            "name": "部门审批",
            "assignment": {"type": "ROLE", "roleCode": "DEPT_HEAD"},
            "next": [
              {"condition": "amount > 5000", "target": "finance_approve"},
              {"condition": "true", "target": "end"}
            ]
          },
          {
            "id": "finance_approve",
            "type": "APPROVAL",
            "name": "财务审批",
            "assignment": {"type": "ROLE", "roleCode": "FINANCE"},
            "next": [{"condition": "true", "target": "end"}]
          },
          {"id": "end", "type": "END", "name": "结束"}
        ]
      },
      "permissions": {
        "start": ["ROLE:USER", "ROLE:ADMIN"],
        "view": ["ROLE:USER", "ROLE:DEPT_HEAD", "ROLE:FINANCE", "ROLE:ADMIN"]
      }
    }
    $process$,
    $schema$
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["amount", "reason", "dept"],
      "properties": {
        "amount": {"type": "number", "title": "报销金额", "minimum": 0.01},
        "reason": {"type": "string", "title": "报销事由", "minLength": 2},
        "dept": {"type": "string", "title": "所属部门", "minLength": 1},
        "remark": {"type": "string", "title": "备注"}
      }
    }
    $schema$,
    $ui$
    {
      "amount": {"ui:widget": "money"},
      "reason": {"ui:widget": "textarea"},
      "remark": {"ui:widget": "textarea"}
    }
    $ui$,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (process_key, version) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    process_json = EXCLUDED.process_json,
    form_schema = EXCLUDED.form_schema,
    form_ui_schema = EXCLUDED.form_ui_schema,
    form_definition_id = NULL,
    form_definition_version = NULL,
    published_at = EXCLUDED.published_at,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
