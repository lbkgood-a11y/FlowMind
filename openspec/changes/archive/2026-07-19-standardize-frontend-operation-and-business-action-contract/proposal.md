## Why

TrioBase is moving from foundational platform pages toward information-dense business systems such as MDM, SCM, WMS, and CRM. Without a shared frontend operation language backed by backend business semantics, each document page will drift into different layouts, button rules, state transitions, validation paths, and audit behavior.

This change standardizes the contract between Vben-based frontend pages and the Spring Cloud/Temporal backend so existing pages and future business modules use the same operation flow, compact component patterns, action semantics, authorization decisions, and document timeline behavior.

## What Changes

- Introduce a TrioBase frontend operation standard on top of the existing Vben application, without changing the Vben shell, router, menu, access, request, theme, or base component architecture.
- Standardize page patterns for single-table, master-detail, multi-table, and document detail pages with compact ERP density, consistent toolbar placement, row actions, confirmations, loading, success/error feedback, and refresh behavior.
- Introduce a platform-level Business Object Catalog for business objects, document status groups, available actions, field/page metadata, permissions, and frontend operation metadata.
- Define the backend as the source of truth for business object state, action availability, disabled reasons, confirmation requirements, danger level, execution mode, and post-action refresh scopes.
- Extend Global Action usage so lifecycle operations such as submit, approve, reject, cancel, close, retry, and workflow-triggering operations go through `service-action`, while queries and draft edits remain in owning domain services.
- Add dynamic ActionDefinition synchronization from owner services instead of hard-coding all future MDM/SCM/WMS/CRM action definitions inside `service-action`.
- Extend Action Candidate validation to support batch page-action availability checks and frontend-renderable reasons.
- Align authorization and guard decisions so frontend buttons are rendered from backend decisions rather than page-local business guesses.
- Extend audit behavior into a unified document timeline that correlates domain events, Global Action events, workflow task events, attachment/import-export events, and operation audit entries.
- Migrate existing pages in phases instead of a one-shot rewrite, starting from Action-heavy pages, complex management pages, and multi-table workbench pages.

## Capabilities

### New Capabilities
- `frontend-operation-standard`: Defines Vben-compatible TrioBase page, component, layout, operation, refresh, and compact-density standards for existing and future business modules.
- `business-object-catalog`: Defines platform-level business object metadata, document status groups, action metadata, page metadata, field metadata, permissions, and owner-service registration behavior.

### Modified Capabilities
- `global-action-runtime`: Add dynamic ActionDefinition synchronization, batch action-candidate availability validation, owner-service execution contract requirements, frontend refresh scopes, and lifecycle-action boundaries.
- `enterprise-authorization-model`: Add action availability decisions with visible/enabled/disabled-reason semantics and clarify composition between central authorization and owner-service state guards.
- `platform-audit-log`: Add unified document timeline requirements that correlate operation audit, Global Action execution, domain business events, workflow task events, and trace metadata.

## Impact

- Frontend:
  - `trio-base-frontend/apps/web-antd/src` module organization, page standards, shared business/page components, Action client usage, and migration tests.
  - Existing pages such as process instance/task, lowcode runtime, OpenAPI workbench, role, user, org, menu, dictionary, config, operations pages.
- Backend:
  - `trio-base-common/common-action` contracts for action metadata, candidate availability, refresh scopes, and owner execution responses.
  - `trio-base-services/service-action` dynamic definition registry, candidate batch validation, dispatch contract, idempotency, events, and result metadata.
  - `trio-base-services/service-auth` authorization decision output and audit correlation.
  - `trio-base-services/service-workflow-engine` extraction or sharing of business object catalog responsibilities.
  - New or refactored `service-business-catalog` capability for MDM/SCM/WMS/CRM-ready metadata.
  - Future `service-mdm`, `service-scm`, `service-wms`, and `service-crm` owner-service templates.
- Governance:
  - Frontend tests or lint checks preventing lifecycle actions from bypassing Action Client.
  - Backend architecture tests requiring owner services to register actions and implement `/internal/v1/actions/execute`.
  - Contract tests for business object metadata, action availability, authorization reasons, and document timeline correlation.
