# Global Action API 契约

Base path 通过平台网关暴露为 `/api/v1/actions`。所有响应继续使用 TrioBase `R<T>` 包装；下文只描述 `data` 内容。

## Submit Action

`POST /api/v1/actions`

提交业务变更动作。

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

```json
{
  "actionId": "act_001",
  "actionType": "process.task.approve",
  "status": "SUCCEEDED",
  "ownerService": "service-workflow-engine",
  "ownerExecutionRef": "TASK001",
  "retryable": false,
  "message": "OK",
  "data": {
    "task": {}
  },
  "errors": []
}
```

Duplicate submissions with the same tenant, action type, and idempotency key return the existing action result instead of dispatching another side effect.

## Get Action Detail

`GET /api/v1/actions/{actionId}`

Returns the persisted action execution detail, including actor, source, target, status, trace id, correlation id, idempotency key, bounded result summary, bounded error summary, owner service, and owner execution reference.

## Get Action Events

`GET /api/v1/actions/{actionId}/events`

Returns ordered lifecycle events. Clients use this for polling fallback when SSE is unavailable.

Event fields:

- `actionId`
- `sequence`
- `eventType`
- `status`
- `message`
- `payload`
- `createdAt`

## Subscribe Action Events

`GET /api/v1/actions/{actionId}/stream`

Server-Sent Events endpoint for action lifecycle updates. The frontend Action Client parses `data:` frames into `ActionEvent`.

The stream ends after a terminal status:

- `SUCCEEDED`
- `FAILED`
- `REJECTED`
- `CANCELLED`
- `COMPENSATED`

## Query Actions

`GET /api/v1/actions`

Admin/audit query endpoint. Supported filters:

- `tenantId`
- `actionType`
- `actorId`
- `source`
- `targetType`
- `targetId`
- `status`
- `traceId`
- `correlationId`
- `idempotencyKey`
- `page`
- `size`

Results are ordered by creation time descending.

## Validate Action Candidate

`POST /api/v1/actions/candidates/validate`

Validates an LUI/Agent proposed `ActionCandidate` against registered action definitions and payload schemas. The result reports whether it is valid, dispatchable, and requires confirmation.

## Dispatch Action Candidate

`POST /api/v1/actions/candidates/dispatch`

Dispatches a previously validated candidate. The bridge rejects unregistered actions, schema-invalid payloads, and sensitive/critical actions without confirmation metadata.

## Owner Adapter

Owner services expose internal adapter endpoints:

`POST /internal/v1/actions/execute`

This endpoint is not public. `service-action` calls it with `ActionOwnerDispatchRequest`; owner services return `ActionOwnerDispatchResponse`.

Owner adapters must propagate:

- action id/type/source
- actor
- target
- context trace/correlation
- idempotency key
- structured guard errors
- owner execution reference

## Audit Fields

Platform operation audit logs expose Action metadata:

- `actionId`
- `actionType`
- `actionSource`
- `actionStatus`
- `actionTargetType`
- `actionTargetId`
- `actionCorrelationId`
- `actionIdempotencyKey`
- `actionSummary`

Audit summaries must be redacted and bounded.
