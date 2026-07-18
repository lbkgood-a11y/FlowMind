## Context

The OpenAPI lifecycle console currently uses one generic resource page with endpoint/action metadata. It can operate the complete lifecycle, but creation and many lifecycle actions require implementers to compose raw JSON payloads. The users of this console are implementation staff, so the default workflow needs to be guided, visual, and tolerant of partial knowledge while still submitting the existing typed backend payloads.

## Goals / Non-Goals

**Goals:**

- Provide visual create and action dialogs for every OpenAPI lifecycle resource page.
- Keep the backend API contract unchanged by generating existing DTO payloads in the frontend.
- Support nested configuration areas such as mapping rules, product routes, contacts, network policies, route predicates, security policies, callback correlation, and orchestration steps.
- Preserve a deliberate advanced JSON mode for troubleshooting, import/export, and unsupported edge cases.
- Make form coverage testable so a newly added lifecycle resource cannot silently fall back to raw JSON-only creation.

**Non-Goals:**

- Replacing typed management APIs with a generic mutation endpoint.
- Building a full external developer portal.
- Building bespoke drag-and-drop designers for mappings or Temporal orchestration in this change.
- Inferring schemas from arbitrary backend Java DTOs at runtime.

## Decisions

### 1. Use frontend form metadata beside resource metadata

Each lifecycle resource will declare a `createForm` and optional action forms in `resource-config.ts`. The metadata describes groups, primitive fields, enum options, arrays, object builders, defaults, visibility, and payload transforms. Keeping it beside the current route/action metadata makes page coverage explicit and avoids backend reflection.

Alternative considered: fetch dynamic JSON Schema from backend. This would be powerful, but the current backend endpoints do not expose UI-oriented schema metadata, and adding that now would create a larger cross-service change.

### 2. Build a reusable dynamic form renderer

`DynamicPayloadForm.vue` renders metadata into Ant Design Vue controls and emits a payload object. This keeps the resource page generic while removing the raw JSON requirement. Nested rows are represented with table-like array groups to support contacts, mapping rules, routes, overrides, and orchestration steps.

Alternative considered: create a bespoke page per asset type. That would produce better UX long term, but it would duplicate lifecycle scaffolding and slow down coverage across all resources.

### 3. Keep advanced JSON as synchronized preview

Create/action dialogs will show a visual tab by default and an advanced JSON tab as a fallback. JSON changes validate on save and can be synchronized back to visual state when possible. This preserves operator power without making JSON the default.

Alternative considered: remove JSON entirely. That would make recovery and debugging difficult for edge cases while metadata coverage is still evolving.

### 4. Start with MVP metadata for all resources, specialized editors for high-friction fields

The first implementation will provide field-level visual forms for structures, value maps, mappings, connectors, routes, products, applications, subscriptions, callbacks, orchestrations, and policies. Specialized affordances are limited to array/object builders and JSON-schema/security-policy helper fields in this change.

## Risks / Trade-offs

- [Metadata drift from backend DTOs] -> Add tests for every lifecycle resource and keep payload keys aligned with existing smoke payloads.
- [Visual forms may not cover every rare policy field] -> Preserve advanced JSON mode and render policy/object fields through guided key-value or compact JSON sub-editors.
- [Large forms can become noisy] -> Group fields by lifecycle concept and keep rarely used settings collapsed.
- [Frontend typecheck already has unrelated process-module failures] -> Add focused tests for OpenAPI helpers and report global typecheck blockers separately.

## Migration Plan

1. Add dynamic payload form metadata and renderer.
2. Switch OpenAPI resource create/action modals to visual-first tabs.
3. Add per-resource create metadata using existing backend DTO payload shapes.
4. Add tests for default payload generation and form coverage.
5. Keep existing JSON payload route available for rollback by leaving advanced mode in place.

Rollback can disable the visual renderer by reverting `resource.vue` to raw JSON mode; backend APIs and data model are unchanged.

## Open Questions

None for the MVP. Further UX refinement can add bespoke mapping/orchestration designers after the metadata-driven baseline is in production.
