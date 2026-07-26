# Global Action API 契约

Global Action 仍使用 `common-action` 的 `GlobalActionRequest`、`GlobalActionResult`、`ActionCandidate` 和 `ActionDefinition` 模型，但执行入口由 owning service 自托管，不再通过中央 `/api/v1/actions` facade。

## Owner-Hosted Base Paths

当前 owner-hosted Action Runtime 入口：

- Lowcode: `/api/v1/lowcode-runtime/actions`
- Workflow: `/api/v1/workflow-actions`
- OpenAPI: `/api/v1/openapi/management/actions`

Action Client 必须根据 `target.ownerService`、ActionDefinition owner 或受控 resolver 选择 owner base path。未知 owner 或未注册 action 必须拒绝。

## Dispatch Action

`POST {owner-base-path}/dispatch`

Request body: `GlobalActionRequest`

```json
{
  "actionType": "process.task.approve",
  "source": "GUI",
  "executionMode": "SYNC",
  "idempotencyKey": "process.task.approve:TASK001:...",
  "actor": {
    "type": "USER",
    "id": "U001",
    "displayName": "Alice",
    "tenantId": "tenant-a"
  },
  "target": {
    "type": "PROCESS_TASK",
    "id": "TASK001",
    "ownerService": "service-workflow-engine",
    "tenantId": "tenant-a"
  },
  "payload": {
    "comment": "同意"
  },
  "context": {
    "traceId": "trace-001",
    "correlationId": "corr-001",
    "locale": "zh-CN",
    "tenantId": "tenant-a"
  }
}
```

Response data: `GlobalActionResult`

## Candidate Validation And Dispatch

- `POST {owner-base-path}/candidates/validate`
- `POST {owner-base-path}/candidates/batch-validate`
- `POST {owner-base-path}/candidates/dispatch`

LUI/Agent 只能生成 `ActionCandidate`。候选动作必须经过 owner-hosted definition、schema、authorization、guard 和 confirmation 校验；确认后才允许 dispatch。

## Definitions

`GET {owner-base-path}/definitions`

返回 owner 服务当前注册的 `ActionDefinition` 列表，用于诊断和受控发现。

## Audit And Timeline

Owner runtime 必须发出或持久化 bounded action audit event，包含：

- `actionId`
- `actionType`
- `source`
- `actor`
- `target`
- `status`
- `traceId`
- `correlationId`
- `idempotencyKey`
- `ownerExecutionRef`
- redacted payload/result/error summary

文档时间线由 `service-business-catalog` 提供：

- `GET /api/v1/business-timeline`
- `POST /internal/v1/business-timeline/events`

`service-business-catalog` 只查询自己的 `bc_document_timeline_event` 投影表。Owner 服务必须通过 bounded timeline event 投递 action/domain/workflow/audit 事件，不得让 timeline 查询跨库读取 owner 表或历史 `act_*` 表。
