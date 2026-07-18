## Why

The OpenAPI lifecycle console is functionally complete, but implementers must still author most create and action payloads as raw JSON. That makes day-to-day delivery error-prone and limits adoption by non-developer implementation staff.

## What Changes

- Replace raw JSON-first create/action dialogs with visual, function-specific dynamic forms for each OpenAPI lifecycle resource.
- Add reusable frontend form metadata that describes fields, groups, nested arrays, object builders, default values, enum options, hints, and payload serialization per resource.
- Provide visual editors for high-friction payload areas: JSON schema fields, mapping rules, connector network policy, route release references, product routes, application contacts, subscription overrides, traffic policies, callback security, and orchestration steps.
- Preserve an advanced JSON preview/edit mode as an explicit fallback, with validation and round-trip synchronization to the visual form.
- Add frontend tests for payload generation and resource coverage so every configured OpenAPI lifecycle page has a visual creation path.

## Capabilities

### New Capabilities
- `openapi-visual-configuration`: Visual, dynamic configuration experience for OpenAPI lifecycle resources used by implementation staff.

### Modified Capabilities
- `openapi-operations-console`: The console SHALL prefer guided visual configuration over raw JSON entry while retaining advanced JSON fallback.

## Impact

- `trio-base-frontend/apps/web-antd/src/views/openapi/lifecycle` gains a reusable dynamic form renderer and per-resource form metadata.
- `resource.vue` switches create/action dialogs from raw text areas to visual form mode with JSON preview.
- Existing typed management APIs remain unchanged; payloads are generated client-side and submitted to the same endpoints.
- Tests cover form metadata completeness, default payload generation, nested dynamic rows, JSON fallback, and action payload handling.
