## Context

The current authorization platform correctly treats semantic resource/action grants as the runtime enforcement source and projects menus from grants. The existing role workbench, however, also uses the resource/action tree as the implementation-person configuration source. That forces non-technical personnel to understand backend contracts and makes business intent unrecoverable after resource mappings change.

The change crosses menu metadata, authorization persistence, compilation, publishing, decision explanation, data and field policy configuration, owner-service manifests, frontend role configuration, migration, and audit. It must preserve tenant isolation and fail-closed runtime behavior, and it must coexist with existing grants during migration.

Stakeholders are implementation personnel who configure customer roles, product and owner-service developers who declare page operations, platform administrators who diagnose mappings, auditors who review changes, and end users whose effective permissions must not expand during migration.

## Goals / Non-Goals

**Goals:**

- Let implementation personnel configure authorization entirely with menus, page access, readable content, business operations, data scope, field restrictions, and plain-language constraints.
- Preserve configuration intent independently from compiled runtime grants.
- Distinguish page access, read capabilities, and operation capabilities while resolving dependencies automatically.
- Provide draft, validation, publication, rollback, drift detection, impact analysis, simulation, and audit as one closed production workflow.
- Keep resource/action grants and owner-service enforcement as the runtime security boundary.
- Migrate existing roles without silently widening effective access.

**Non-Goals:**

- Infer production authorization mappings automatically from route names, button text, or HTTP paths.
- Let implementation personnel create or repair technical resource/action mappings.
- Replace owner-service authorization enforcement with frontend visibility controls.
- Make every operation use an independent data scope by default; operation overrides remain explicit exceptions.
- Delete legacy grants or the legacy workbench before compatibility evidence is accepted.

## Decisions

### Page capability intent is the management source

Persist tenant-scoped role assignments to stable page capability codes. A page capability is categorized as `ACCESS`, `READ`, or `OPERATION`, carries business-language labels and help text, and may declare dependencies and configurable policy features. Published intent is compiled into `sys_auth_grant` and existing data/field/guard policy projections.

This replaces the previous management-source assumption but not the runtime-source contract: owner services continue to authorize semantic resource/actions. Keeping only generated grants was rejected because reverse mapping is ambiguous and mapping changes would erase the implementer's original business intent.

### Explicit manifest registration, not UI or endpoint inference

Product modules and owner services register a versioned page-capability manifest containing menu identity, capability codes, categories, display metadata, dependency codes, target resource/actions, scope support, field-enforcement support, and guard requirements. Low-code publication generates the same contract through its governed registry.

Automatic inference from buttons or routes was rejected because identical labels can represent different operations and a single business operation can invoke several actions or workflows.

### Separate catalog, intent, release, and compiled projection

The persistence model separates:

- page capability catalog and versioned target mappings;
- role capability draft intent, including default page scope and explicit operation overrides;
- immutable role authorization releases with validation evidence and actor metadata;
- compiled grant and policy projections linked to a release and mapping version.

Compilation runs transactionally per role release. A release becomes active only after validation and projection persistence succeed. Existing active projection remains in force on failure.

### Dependencies are deterministic and visible

Capabilities declare directed dependencies. Selecting an operation automatically selects and locks required access/read capabilities. Removing a required capability either removes dependents after explicit confirmation or is rejected by the API. Cycles and missing dependencies block catalog readiness and publication.

Implicit dependencies in frontend code were rejected because they would diverge between management UI and backend compilation.

### Read scope and operation scope are distinct

Each page has a default data scope used by applicable capabilities. An operation can declare an explicit override only when its manifest says scope is supported. The business summary always names the effective scope for read and exceptional operations. Compilation never widens an omitted or unsupported scope; unsupported combinations fail validation.

### Validation and publication are separate operations

Draft changes do not affect production access. Validation returns blocking errors, warnings, auto-resolved dependencies, effective business-language summaries, affected users, and compiled diff. Publication requires a validation token bound to draft version, catalog version, tenant, role, and actor authority.

This prevents stale validation results and supports least-privilege review before production changes.

### Mapping readiness and drift are first-class states

Each page and capability exposes `READY`, `PARTIAL`, `BROKEN`, or `UNMAPPED` readiness with plain-language implementation guidance and expandable technical evidence for platform administrators. Active mapping changes create drift records and impact analysis; they never silently recompile active releases. Administrators must validate and publish a new release.

### Business-language UI with isolated diagnostics

The role workbench follows: choose pages, choose read and operations, configure data/field/constraint details, review summary and diff, validate with a real user or role simulation, then publish. Technical codes are absent from the normal flow. A separate administrator-only diagnostic view exposes mappings, services, codes, versions, and failures.

### Runtime enforcement remains layered and fail-closed

Published projections drive dynamic menus and route access; batch decisions drive button availability; owner services enforce resource actions, query scopes, field rules, and guards. Missing compiled evidence, stale required projection, unknown resources, or unsupported enforcement denies access rather than widening it.

### Audit records business intent and technical evidence

Audit events store draft changes, auto-added dependencies, validation results, release diffs, publication or rollback, impacted users, compiled projection references, trace IDs, and actor identity. The default audit explanation uses menu and operation names, with technical evidence available only to authorized platform administrators.

## Risks / Trade-offs

- [Two sources appear to exist during migration] → Mark role management mode explicitly, link every compiled projection to a release, compare decisions, and block dual writes after cutover.
- [Capability mappings can broaden access] → Require impact analysis and explicit publication; never auto-recompile active releases.
- [Manifest authors can declare incomplete enforcement] → Validate target existence and verified owner capabilities, require contract tests, and mark incomplete capabilities non-publishable.
- [Per-operation scopes can overwhelm implementers] → Provide a page default and show overrides only when requested or required by the operation.
- [Dependency graphs can become complex] → Enforce acyclic graphs, cap depth, show automatic additions, and test compilation deterministically.
- [Large tenants may have expensive impact analysis] → Use versioned snapshots and asynchronous preview jobs while publication remains bounded and transactional.
- [Legacy roles may not reverse-map uniquely] → Migrate to an explicit review-required draft when exact reconstruction is impossible and keep the old active projection until approved.

## Migration Plan

1. Add catalog, intent, release, compilation, and audit tables without changing active decisions.
2. Register manifests for system-management pages and validate owner-service targets and enforcement metadata.
3. Build legacy grant-to-capability migration analysis with `EXACT`, `PARTIAL`, and `UNMAPPED` results; do not activate migrated drafts automatically.
4. Enable the new role workbench behind a tenant feature flag and compare compiled preview decisions with legacy effective decisions.
5. Allow publication only when the new result does not unintentionally widen permissions and all blocking mappings are ready.
6. Switch role management to page-capability intent while retaining generated grants for runtime and rollback.
7. After a production compatibility period, disable legacy grant editing and later remove the legacy workbench under its existing removal task.

Rollback reactivates the previous immutable release and its compiled projections. Schema and catalog data remain for diagnosis; rollback never reconstructs permissions from current mappings.

## Open Questions

- Which existing system-management pages form the first production migration cohort after user, tenant, audit, session, and system-parameter pages?
- Which organization-scope presets require customer-specific terminology aliases without changing their semantic codes?
- What approval policy, if any, should large permission expansions require beyond the publisher's own authority?
