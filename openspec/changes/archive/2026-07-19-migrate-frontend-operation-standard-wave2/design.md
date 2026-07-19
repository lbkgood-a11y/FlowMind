## Context

The first operation-standard implementation added shared page, action, document, business metadata, compact density, and pilot migrations for process instance, role management, and OpenAPI workbench pages. The active `web-antd` app still has many page-local implementations in system, process, lowcode, OpenAPI operations, and operations management modules.

This second wave must improve user-facing consistency without changing the Vben shell or reworking the backend Action contract again. The work should convert existing pages to the shared TrioBase business-layer primitives where they are stable enough, and leave domain-specific APIs intact for query, management CRUD, and draft-edit operations.

## Goals / Non-Goals

**Goals:**
- Expand the standard frontend operation experience beyond the first three pilot pages.
- Make system/process/lowcode/OpenAPI/operations pages use common page scaffolds, query bars, toolbars, table frames, and drawer/detail layout rules.
- Route lifecycle operations through the existing Action Client and use backend `refreshScopes` where Action results are available.
- Add tests and documentation that make the migrated page set explicit and protected.
- Keep pages compact and information-dense for future MDM, SCM, WMS, and CRM document-heavy modules.

**Non-Goals:**
- Replacing Vben shell, menus, router, access control, request client, or theme architecture.
- Forcing management CRUD, list querying, pagination, sorting, or draft edits through Global Action.
- Rebuilding every view in one pass, including demos, login/profile/core fallback pages, and low-priority sample pages.
- Changing backend Action runtime contracts unless a frontend migration reveals a blocking contract bug.

## Decisions

### Decision 1: Migrate by page family, not by visual file count

Second wave migration groups pages by operation family:
- System management: user, org/dept, menu, dictionary, config, session, audit, authz, role follow-up.
- Process runtime: package, task, instance follow-up, designer shell where appropriate.
- Lowcode: form list, runtime center, runtime app, compatibility cleanup.
- OpenAPI: lifecycle overview/resource pages and operations execution/quarantine pages.
- Operations: announcement, file, import/export, job, message.

This keeps behavior coherent inside each module and makes review easier than isolated one-file rewrites.

### Decision 2: Standard wrappers first, deeper refactors only when the page already exposes lifecycle actions

Pages with mostly query and management operations should first adopt `BusinessPageScaffold`, `CompactQueryBar`, `CompactToolbar`, and `CompactTableFrame`. Pages with submit/approve/retry/execute/cancel style operations should additionally use `BusinessActionButton`, `BusinessActionMenu`, `ActionConfirmationWrapper`, `useActionAvailability`, and `refreshByScopes`.

This keeps the migration practical while preserving the Global Action boundary.

### Decision 3: Existing business APIs remain the source for query and draft state

Domain query APIs, option APIs, table reads, and draft-save APIs remain in their existing modules. The migration only standardizes the page operation shell and dispatches true lifecycle operations through Action Client.

### Decision 4: Governance grows with migrated coverage

The frontend guard test should maintain a list of migrated pages. For those pages, new lifecycle operation wrappers must use the Action Client or be explicitly classified as non-lifecycle. A second page-standard test should assert that migrated pages import the shared operation primitives.

## Risks / Trade-offs

- [Risk] Large pages such as user, menu, lowcode runtime, and OpenAPI lifecycle may need multiple passes. -> Mitigation: migrate wrappers and layout first, then lifecycle actions and dense document components where relevant.
- [Risk] Existing dirty worktree contains older changes from previous phases. -> Mitigation: touch only files needed for wave two and avoid reverting unrelated changes.
- [Risk] Visual consistency can regress without screenshots. -> Mitigation: add source-level tests now and reserve Playwright visual review for pages with a running backend or mock data.
- [Risk] Some management pages have bespoke UX that does not map perfectly to shared primitives. -> Mitigation: keep the shared primitives flexible and document intentional exceptions.

## Migration Plan

1. Create the second-wave migration inventory and classify pages by family and operation type.
2. Add missing shared helpers only when multiple pages need them.
3. Migrate system management pages to the compact page/query/toolbar/table/drawer pattern.
4. Migrate process task/package pages and ensure lifecycle operations continue through Action Client.
5. Migrate lowcode runtime/form pages while keeping query/draft APIs lightweight.
6. Migrate OpenAPI operations/lifecycle and operations management pages to shared layout primitives.
7. Extend frontend guard tests and migration documentation.
8. Run frontend typecheck and targeted unit/source tests.
