# TrioBase Document Timeline Standard

## Purpose

Business document pages show one timeline assembled from bounded domain events and platform Action events. Owner services keep domain state ownership, while `service-action` provides the shared timeline contract and read model for frontend pages.

## Event Sources

- `DOMAIN_EVENT`: owner-service events for create, draft edit, status change, attachment, import/export, and workflow callback outcomes.
- `GLOBAL_ACTION`: normalized action execution records from `act_action_execution`.
- `ACTION_EVENT`: detailed action lifecycle records from `act_action_event`.
- `WORKFLOW_EVENT`: workflow task or process events recorded by owner services through the same bounded event endpoint.
- `OPERATION_AUDIT`: management or platform audit entries that are converted to bounded timeline events when they affect a business document.

## Recording Contract

Owner services record document timeline events through `POST /internal/v1/business-timeline/events` with `BusinessTimelineEventRecord`.

Required fields:
- `tenantId`
- `targetType`
- `targetId`
- `eventType`

Recommended correlation fields:
- `actionId`
- `actionType`
- `actionStatus`
- `ownerExecutionRef`
- `traceId`
- `correlationId`
- `actorId`
- `actorName`

## Bounded Summary Rules

- Do not store raw before/after sensitive values.
- Store changed field keys, new status, business result, counts, filenames, workflow node names, and compact failure codes.
- Keys containing password, secret, token, credential, id card, identity, phone, mobile, bank, or account are redacted by `service-action`.
- Large payloads belong in owner-service storage; timeline summaries are only for display and investigation context.

## Query Rules

Frontend document pages call `GET /api/v1/business-timeline` with `tenantId` and either document target filters or correlation filters. Ordinary document timeline queries must include `tenantId` to preserve tenant boundary. Returned entries are sorted by occurrence time and include a `redacted` flag for UI display.
