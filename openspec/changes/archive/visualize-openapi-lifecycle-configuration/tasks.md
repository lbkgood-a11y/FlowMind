## 1. Dynamic Form Foundation

- [x] 1.1 Define visual payload form field/group metadata types and payload generation helpers
- [x] 1.2 Implement a reusable Ant Design Vue dynamic payload form renderer with primitive, enum, boolean, number, textarea, object, and array controls
- [x] 1.3 Add synchronized advanced JSON preview/edit mode with syntax validation and visual state reset

## 2. OpenAPI Resource Coverage

- [x] 2.1 Add create form metadata for structures, value maps, mappings, connectors, routes, products, applications, subscriptions, callbacks, orchestrations, and policies
- [x] 2.2 Add lifecycle action form metadata for body-required actions such as release creation, rollback, subscription upgrade, quarantine resolution, mapping preview, and lookup
- [x] 2.3 Provide useful defaults, placeholders, and grouped field labels for implementation staff while preserving backend DTO payload shapes

## 3. Console Integration

- [x] 3.1 Replace raw JSON create dialogs in the OpenAPI resource page with visual-first dynamic forms and advanced JSON fallback
- [x] 3.2 Replace raw JSON action dialogs with action-aware visual forms and bodyless action handling
- [x] 3.3 Keep permission-aware create/action visibility unchanged

## 4. Validation

- [x] 4.1 Add frontend unit tests for payload generation, nested rows, JSON fallback validation, and resource metadata coverage
- [x] 4.2 Run OpenSpec validation and focused frontend tests/type checks for the OpenAPI lifecycle module
- [x] 4.3 Re-run the OpenAPI lifecycle smoke path to confirm backend payload compatibility

## 5. Reference Selection

- [x] 5.1 Add lifecycle catalog asset types for version/client references used by visual selectors
- [x] 5.2 Add searchable reference select controls that show code/key labels and submit DTO values
- [x] 5.3 Replace visual-mode raw ID fields and action targets with reference selectors while keeping advanced JSON visible
- [x] 5.4 Add focused tests and rerun validation after reference selector integration

## 6. Reference Catalog Guardrails

- [x] 6.1 Scope version reference catalogs through tenant-filtered parent assets
- [x] 6.2 Add catalog tests for readable version labels and tenant-parent guardrails
- [x] 6.3 Rerun focused backend/frontend/OpenSpec validation after guardrail integration

## 7. Structured Detail, Editing, And Demo Data

- [x] 7.1 Replace raw JSON-only asset details with structured readable lifecycle detail rendering
- [x] 7.2 Add visual draft edit metadata and modal integration for mutable lifecycle resources
- [x] 7.3 Add missing typed update support for application and traffic policy drafts
- [x] 7.4 Add a local OpenAPI reset-and-demo data script scoped to OpenAPI tables
- [x] 7.5 Clear local OpenAPI data, create implementer demo data, and rerun focused validation
