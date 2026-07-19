## Context

The current frontend is a Vben/Vue monorepo with `apps/web-antd` as the active business application. Vben already provides shell-level capabilities such as layout, menu, router, access, request, theme, and base UI packages. TrioBase-specific business components and page standards are still thin, while several existing pages are large and mix query state, table definitions, modal/drawer state, button logic, API calls, permissions, Action dispatch, and local layout styling.

The backend already contains the right foundations: `common-action` defines Global Action contracts, `service-action` owns action submission, validation, idempotency, audit, event, and owner dispatch, `service-auth` owns authorization/resource/field/data-scope decisions, and `service-workflow-engine` owns Temporal workflow/task execution. Existing owner services such as lowcode, workflow, and openapi already expose internal action execution adapters.

The next business expansion will add MDM, SCM, WMS, and CRM modules. These systems will be dominated by information-dense document operations. The architecture therefore needs a shared frontend operation language and backend business-operation contract before more pages and services are added.

## Goals / Non-Goals

**Goals:**
- Keep the existing Vben shell architecture stable while adding TrioBase business-layer standards.
- Standardize frontend operation patterns for single-table, master-detail, multi-table, and document pages.
- Make backend metadata and decisions the source of truth for business object state, action availability, disabled reasons, confirmation requirements, execution mode, and refresh scopes.
- Ensure lifecycle actions use Global Action consistently while ordinary query and draft-edit operations remain in owner domain APIs.
- Move business object metadata from a workflow-local concern toward a platform-level catalog that can serve MDM, SCM, WMS, CRM, lowcode, process, and openapi modules.
- Provide a phased migration path for existing pages instead of a risky all-at-once rewrite.

**Non-Goals:**
- Replacing Vben, changing the shell layout system, or introducing micro-frontend architecture as part of this change.
- Forcing all CRUD operations through Global Action.
- Implementing the full MDM, SCM, WMS, or CRM business domains in this change.
- Rewriting all existing pages in one implementation pass.
- Moving business state ownership out of domain services into `service-action`.

## Decisions

### Decision 1: Keep Vben as the Stable Shell

TrioBase will not modify Vben's shell, router, menu, access, request, theme, or base UI architecture. The change adds TrioBase business-layer standards under app-local shared modules such as `shared/page`, `shared/action`, `shared/document`, `shared/table`, `shared/form`, and `shared/business`.

Alternative considered: replace or deeply customize Vben layout. This was rejected because the current issue is business-operation consistency, not shell capability.

### Decision 2: Use Modular Monolith Before Multi-App Split

The frontend will remain a single Vben app while business modules are isolated under module boundaries such as `system`, `process`, `lowcode`, `openapi`, `mdm`, `scm`, `wms`, and `crm`. Each module owns its routes, APIs, action metadata, permissions, pages, components, composables, and types.

Alternative considered: immediately splitting MDM/SCM/WMS/CRM into separate frontend apps. This was rejected until independent teams, release cadence, deployment boundaries, or bundle pressure justify the extra operational complexity.

### Decision 3: Separate Operation Categories

Operations are classified as:
- Local UI Operation: tab switches, drawer toggles, row selection, local editing state.
- Query Operation: filters, pagination, sorting, refresh, detail loading.
- Management Operation: platform or configuration CRUD such as users, roles, menus, dictionaries, jobs, and system config.
- Business Action: lifecycle or side-effect operations such as submit, approve, reject, cancel, close, retry, confirm, execute, workflow start, workflow signal, and external state-changing invocation.

Only Business Actions are required to use Global Action. This keeps Action valuable without turning every basic edit into a heavyweight workflow.

### Decision 4: Introduce a Platform Business Object Catalog

Business object metadata will become a platform capability rather than staying only inside `service-workflow-engine`. The catalog describes object types, owner service, status groups, action metadata, page metadata, field metadata, permission mapping, and tenant overrides.

`service-workflow-engine` can remain a consumer and contributor of process-related object metadata, but it must not be the only source for MDM/SCM/WMS/CRM business object definitions.

### Decision 5: Backend Owns Action Availability

Frontend buttons for business actions must be rendered from backend candidate/availability results. The frontend may perform optimistic local UX checks, but final visible/enabled/disabled reason, confirmation requirement, danger level, execution mode, and target status metadata come from backend decisions.

This prevents each page from duplicating state-transition, permission, or guard logic.

### Decision 6: Action Definitions Become Owner-Synchronized

Future owner services will synchronize their ActionDefinitions into `service-action` through an internal definition-sync contract. `service-action` keeps persisted snapshots and runtime definitions, while owner services remain responsible for business execution.

This avoids hard-coding every MDM/SCM/WMS/CRM action provider inside `service-action`.

### Decision 7: Owner Services Use a Standard Execution Adapter

Every domain service that owns Business Actions implements `/internal/v1/actions/execute` and receives `ActionOwnerDispatchRequest`. The service validates owner-specific guards, performs state changes, starts or signals Temporal workflows when needed, and returns `ActionOwnerDispatchResponse`.

Short synchronous operations can return terminal success or failure. Long-running workflow or asynchronous operations return accepted/running metadata with owner execution references.

### Decision 8: Document Timeline Correlates Events

Document detail pages need one consistent timeline. The backend will correlate domain business events, Global Action events, workflow task events, operation audit entries, attachment/import-export events, trace id, correlation id, actor, and target document identity.

The timeline is a read model. It does not replace existing action execution, audit, or workflow storage.

## Risks / Trade-offs

- [Risk] The scope is cross-cutting across frontend, action runtime, authorization, audit, and domain services. -> Mitigation: deliver in phases, beginning with metadata contracts, Action candidate availability, and three representative existing page migrations.
- [Risk] Business Object Catalog can become an overdesigned metadata platform. -> Mitigation: model only the fields needed for current operation consistency, then expand when MDM/SCM/WMS/CRM require it.
- [Risk] Backend-driven availability can add round trips and latency. -> Mitigation: support batch candidate validation and cache stable catalog metadata while keeping state-sensitive availability fresh.
- [Risk] Existing pages may temporarily mix old and new patterns. -> Mitigation: mark migrated pages, add guard tests for lifecycle action bypass, and migrate page families in defined waves.
- [Risk] Dynamic ActionDefinition registration can introduce drift between owner services and `service-action`. -> Mitigation: persist versioned definition snapshots, expose diagnostics, and fail closed when runtime definitions are missing or incompatible.
- [Risk] Document timelines may expose sensitive payload data. -> Mitigation: use redacted summaries, field-level authorization, and bounded event payloads.

## Migration Plan

1. Add Business Object Catalog contracts and initial metadata model without changing existing runtime behavior.
2. Extend Global Action contracts for dynamic definition sync, batch candidate availability, refresh scopes, and target status metadata.
3. Add owner-service template support for definition sync and `/internal/v1/actions/execute`.
4. Connect authorization decisions and owner guards to candidate availability output.
5. Add frontend shared standards and compact page/document components on top of Vben.
6. Migrate pilot pages:
   - Action-heavy page: process instance or task operation page.
   - Complex management page: role or user page.
   - Multi-table workbench page: OpenAPI workbench.
7. Add governance tests that prevent lifecycle actions from bypassing Action Client and owner execution.
8. Migrate remaining existing pages by page family.
9. Use the same module/service template for future MDM, SCM, WMS, and CRM services.

Rollback is handled per phase: keep old domain APIs for query and draft-edit operations, preserve existing Vben shell behavior, and migrate only selected pages until the new standards are verified.

## Open Questions

- Whether Business Object Catalog should be a standalone `service-business-catalog` immediately or first implemented as a platform package/service slice before extraction.
- Which existing page should be the first pilot: `process/instance`, `system/role`, or `openapi/workbench`.
- Whether document timeline reads should be served by the catalog service, action service, or a dedicated timeline query endpoint in each owner service with a shared response contract.
