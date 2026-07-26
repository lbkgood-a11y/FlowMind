# TrioBase Document Timeline Standard

## Purpose

Business document pages show one timeline assembled from bounded domain events and platform Action events. Owner services keep domain state ownership, while `service-business-catalog` owns the shared timeline projection and read model for frontend pages. The timeline service reads only its own `bc_document_timeline_event` table; it must not query owner-service tables or historical `act_*` tables at request time.

See also: [Business Catalog Architecture](business-catalog-architecture.md).

## Event Sources

- `DOMAIN_EVENT`: owner-service events for create, draft edit, status change, attachment, import/export, and workflow callback outcomes.
- `OWNER_ACTION_EVENT`: owner-hosted action audit events emitted by lowcode, workflow-engine, OpenAPI, or later owner services.
- `WORKFLOW_EVENT`: workflow task or process events recorded by owner services through the same bounded event endpoint.
- `OPERATION_AUDIT`: management or platform audit entries that are converted to bounded timeline events when they affect a business document.

## Recording Contract

Owner services record document timeline events through `service-business-catalog` at `POST /internal/v1/business-timeline/events` with `BusinessTimelineEventRecord`.

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

Idempotency:
- `eventId` should be stable for retries. `service-business-catalog` stores it as the timeline row id, so owner services should derive it from the owner event id, action id plus event type, or outbox id.
- Owners should emit through a reliable local outbox or equivalent retryable mechanism. Direct internal HTTP calls are acceptable for the current MVP only when the owner operation can safely retry with the same `eventId`.

## Bounded Summary Rules

- Do not store raw before/after sensitive values.
- Store changed field keys, new status, business result, counts, filenames, workflow node names, and compact failure codes.
- Keys containing password, secret, token, credential, id card, identity, phone, mobile, bank, or account are redacted by the business timeline projection before storage.
- Large payloads belong in owner-service storage; timeline summaries are only for display and investigation context.

## Query Rules

Frontend document pages call `GET /api/v1/business-timeline` with `tenantId` and either document target filters or correlation filters. Ordinary document timeline queries must include `tenantId` to preserve tenant boundary. Returned entries are sorted by occurrence time and include a `redacted` flag for UI display.

The read path uses database pagination over `bc_document_timeline_event`. New sources must first project events into this table instead of adding cross-service joins or in-memory merge pagination.
