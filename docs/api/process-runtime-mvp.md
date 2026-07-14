# Process Runtime MVP API Contracts

All public endpoints are routed through `platform-gateway` under `/api/v1`.
Internal participant and form endpoints are direct service-to-service calls and
require `X-Internal-Service-Token` plus `X-Internal-Service-Name` headers.

## Process Package Versioning

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/process-packages` | Create version 1 draft |
| `PUT` | `/process-packages/{id}` | Update draft content only |
| `POST` | `/process-packages/{id}/versions` | Derive the next draft from the latest non-draft version |
| `PUT` | `/process-packages/{id}/publish` | Validate and publish immutable flow/form snapshots |
| `PUT` | `/process-packages/{id}/offline` | Take a published version offline |

Published and offline versions cannot be edited. One process key can have at
most one draft, while multiple immutable published versions may coexist.

## Internal Resolution

| Service | Method and path | Result |
|---|---|---|
| auth | `GET /internal/v1/participants/roles/{roleCode}/users` | Enabled users for a role |
| auth | `POST /internal/v1/participants/users/validate` | Enabled direct users |
| org | `GET /internal/v1/org/units/{unitCode}/users` | Enabled users for an organization unit/dimension |
| lowcode | `GET /internal/v1/forms/{id}/published` | Published Schema and UI Schema snapshot |

The workflow Activity uses sub-500ms timeouts, bounded retries, and TraceId
propagation. Resolution results are persisted as immutable node snapshots.

## Process Start And Form Errors

`POST /process-instances/start` accepts:

```json
{
  "processKey": "expense_report",
  "processPackageId": "PKG001",
  "version": 1,
  "title": "July travel expense",
  "formData": { "amount": 8000, "reason": "Travel", "dept": "TECH" }
}
```

The service resolves the latest published version and rejects stale package ID
or version assumptions with code `40900` and message
`PROCESS_VERSION_CONFLICT`. The response data contains requested and current
package IDs and versions.

Schema failures use code `40000`, message `FORM_DATA_VALIDATION_FAILED`, and
structured field errors:

```json
{
  "code": 40000,
  "message": "FORM_DATA_VALIDATION_FAILED",
  "data": {
    "fieldErrors": [
      { "field": "amount", "code": "TYPE_MISMATCH", "keyword": "type", "message": "..." },
      { "field": "reason", "code": "REQUIRED", "keyword": "required", "message": "..." }
    ]
  }
}
```

Validation occurs before process instance insertion and before Temporal Workflow
startup.

## Task Operations

Every command accepts a caller-generated `operationId` of at most 64 characters.
Retrying the same operation ID for the same task/action returns the existing
result without a second Workflow transition.

| Method | Path | Key request fields |
|---|---|---|
| `POST` | `/tasks/{id}/approve` | `operationId`, `comment` |
| `POST` | `/tasks/{id}/reject` | `operationId`, optional `targetNodeId`, `comment` |
| `POST` | `/tasks/{id}/transfer` | `operationId`, `newAssigneeId` |
| `POST` | `/tasks/{id}/add-sign` | `operationId`, `assigneeId` |
| `GET` | `/tasks/reject-targets/{processInstanceId}` | Previously completed eligible nodes |

Reject without a target terminates the process. Reject with a validated target
re-enters that visited node with a fresh participant snapshot. Transfer creates
a linked pending task; add-sign creates a linked parallel mandatory task.

## History

`GET /process-instances/{id}/history` returns ordered node visit records and
immutable task operation records, including visit number, assignee snapshot,
operator, action, task linkage, target, comment, TraceId, and timestamps.
