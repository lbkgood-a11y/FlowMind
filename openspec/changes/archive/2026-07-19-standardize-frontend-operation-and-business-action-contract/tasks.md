## 1. Baseline and Classification

- [x] 1.1 Inventory existing `web-antd` pages by page pattern: single-table, master-detail, multi-table, document detail, workflow/action page, and low-priority legacy/demo page.
- [x] 1.2 Classify existing frontend operations into Local UI Operation, Query Operation, Management Operation, and Business Action.
- [x] 1.3 Identify existing lifecycle actions that already use `useActionDispatch` and lifecycle actions that still bypass the Action Client.
- [x] 1.4 Define the first migration wave with one Action-heavy page, one complex management page, and one multi-table workbench page.

## 2. Shared Contracts

- [x] 2.1 Extend `common-action` contracts with action availability metadata: visible, enabled, disabled reason, danger flag, confirmation metadata, execution mode, and target status metadata.
- [x] 2.2 Extend `common-action` result contracts with frontend refresh scopes and owner execution metadata.
- [x] 2.3 Add shared DTOs for Business Object Catalog manifests, object metadata, status groups, page metadata, field metadata, and action metadata.
- [x] 2.4 Add contract tests for Action availability, refresh scopes, and Business Object Catalog serialization.

## 3. Business Object Catalog

- [x] 3.1 Decide whether to implement the catalog as a new `service-business-catalog` module or a platform service slice with a later extraction path.
- [x] 3.2 Implement catalog persistence for business object types, owner service, status metadata, action metadata, field metadata, page metadata, permission mappings, tenant overrides, and version state.
- [x] 3.3 Implement internal owner-service manifest synchronization with validation and versioned publishing.
- [x] 3.4 Implement catalog query APIs for frontend object metadata and owner-service/process-engine consumption.
- [x] 3.5 Migrate or bridge workflow business object catalog reads so `service-workflow-engine` can consume platform catalog metadata.
- [x] 3.6 Add tests for global metadata, tenant overrides, offline objects, invalid manifests, and workflow catalog compatibility.

## 4. Action Runtime Enhancements

- [x] 4.1 Implement dynamic ActionDefinition synchronization endpoint in `service-action`.
- [x] 4.2 Persist versioned ActionDefinition snapshots and expose diagnostics for missing, duplicate, or incompatible definitions.
- [x] 4.3 Implement batch Action Candidate availability validation.
- [x] 4.4 Compose payload validation, central authorization, confirmation requirements, and owner guard results into candidate availability output.
- [x] 4.5 Add refresh scope and target status metadata to `GlobalActionResult` creation and owner response adaptation.
- [x] 4.6 Update lowcode, workflow, and openapi owner adapters to preserve the enhanced result metadata.
- [x] 4.7 Add tests for dynamic definition registration, batch candidate validation, disabled reasons, refresh scopes, and owner dispatch compatibility.

## 5. Authorization and Guard Integration

- [x] 5.1 Extend authorization decision DTOs to support action availability rendering metadata where needed.
- [x] 5.2 Add semantic business resource/action code conventions for document actions while preserving URL permission behavior for management pages.
- [x] 5.3 Add batch decision support or adapters for document page action and field rendering.
- [x] 5.4 Implement owner-service state guard hooks for candidate availability and final execution checks.
- [x] 5.5 Add tests for permission-allowed/state-denied, permission-denied/visible-disabled, hidden action, and field readonly/hidden enforcement scenarios.

## 6. Audit and Document Timeline

- [x] 6.1 Define shared timeline entry DTOs and query criteria for document target, action id, trace id, correlation id, actor, event type, and time range.
- [x] 6.2 Implement domain event recording conventions for draft edits and lifecycle status updates.
- [x] 6.3 Correlate Action execution events, operation audit logs, workflow task events, and owner domain events into a document timeline read model.
- [x] 6.4 Apply redaction, tenant boundary, field authorization, and data-scope filtering to timeline responses.
- [x] 6.5 Add tests for timeline correlation, sensitive field redaction, unauthorized access, and trace-based investigation.

## 7. Frontend Operation Standard

- [x] 7.1 Add TrioBase business-layer module conventions under `apps/web-antd/src` without changing Vben shell architecture.
- [x] 7.2 Implement shared compact page primitives: page scaffold, query bar, toolbar, table frame, split layout, master-detail layout, and multi-table layout.
- [x] 7.3 Implement document primitives: document page, document header, status tag, action bar, editable grid frame, side panel, footer summary, and timeline panel.
- [x] 7.4 Implement action primitives: action button, more-actions menu, confirmation wrapper, result feedback, loading state, disabled-reason tooltip, and refresh-scope handler.
- [x] 7.5 Implement frontend clients/composables for Business Object Catalog metadata and batch Action Candidate availability.
- [x] 7.6 Add compact ERP density tokens for gutters, gaps, control height, table row height, radius, query columns, drawer widths, and sticky action areas.
- [x] 7.7 Add frontend tests for action rendering, confirmation, disabled reasons, refresh scopes, and compact layout behavior.

## 8. Existing Page Migration

- [x] 8.1 Migrate the selected Action-heavy pilot page to backend-provided action availability and shared Action UI primitives.
- [x] 8.2 Migrate the selected complex management page to standard query, toolbar, table, drawer/detail, feedback, and management operation patterns.
- [x] 8.3 Migrate the selected multi-table workbench page to standard multi-table layout, region refresh, and shared action/result handling.
- [x] 8.4 Remove redundant page-local operation helpers from migrated pages when shared primitives replace them.
- [x] 8.5 Document migration notes for the next page waves: system, process, lowcode, openapi, operations, then future MDM/SCM/WMS/CRM modules.

## 9. Governance and Verification

- [x] 9.1 Add frontend guard tests or lint rules preventing migrated lifecycle actions from bypassing the Action Client.
- [x] 9.2 Add backend architecture tests requiring Business Action owner services to register definitions and implement the standard internal execution endpoint.
- [x] 9.3 Add contract tests proving frontend action availability output matches backend Action/Auth/Catalog semantics.
- [x] 9.4 Add migration documentation that explains which operations use domain APIs, management operation wrappers, or Global Action.
- [x] 9.5 Run backend unit/contract tests and frontend unit/source tests for affected modules.
- [x] 9.6 Validate the OpenSpec change after artifacts and implementation tasks are complete.
