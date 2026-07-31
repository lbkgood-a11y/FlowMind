## Context

Role authorization is currently presented as six independent tabs even though the model is causal: function grants derive menu visibility, function grants constrain data and field rules, action manifests imply guard requirements, and decision preview evaluates an actual subject. The backend already derives role menus from grants and computes field rules, but the UI does not explain those relationships and runtime field enforcement is uneven across owner services.

Stakeholders are implementation consultants configuring roles, platform administrators maintaining authorization manifests, owner-service developers enforcing decisions, and auditors validating effective access. The design must remain fail-closed, tenant-scoped, explainable, and compatible with existing grants and policies.

## Goals / Non-Goals

**Goals:**

- Make role configuration follow the implementation sequence and expose the effective result at each step.
- Present menu visibility as a projection of function grants, with mapping diagnostics.
- Make data-scope and field-access policies understandable without requiring knowledge of storage records.
- Distinguish policy calculation from runtime enforcement and prevent misleading configuration.
- Support actual-user validation and deterministic current-role simulation.
- Keep guard-template administration outside role configuration while showing action-implied constraints.

**Non-Goals:**

- Replace `sys_auth_grant`, data-policy, field-policy, or guard-template persistence models.
- Let `service-auth` call domain services to evaluate state-sensitive guards.
- Create a second menu authorization source.
- Guarantee field enforcement for a resource that has not declared and implemented an owner-service adapter.

## Decisions

### Guided workbench with effective-result summaries

The role drawer will use five ordered sections: Function & Menu, Data Scope, Field Access, Business Constraints, and Validation. Each section combines configuration with a read-only effective summary. This is preferred over independent tabs because implementers need to understand downstream effects before proceeding.

### Menu visibility remains a projection

Function grants remain the only writable source. A menu-derivation API/view model will return each menu node with `DIRECT_GRANT`, `ANCESTOR`, `UNMAPPED`, or `NOT_GRANTED` evidence and the mapped resource/action code. The role UI will not submit menu IDs independently.

### Data scope uses a structured summary model

The UI will configure resource/action, scope type, organization dimension, and selected organizations, then render a normalized sentence and conflict explanation from structured policy data. Effective scope remains calculated by the authorization service; the UI does not infer broader access.

### Field enforcement is an advertised owner capability

Authorization manifests will declare read-hide, read-mask, and write-deny enforcement support. The registry exposes this metadata. The workbench disables unsupported operations and states when a rule is decision-only. Advertising support is a contract: owner services must apply rules server-side and pass contract tests.

Alternative considered: allow configuration for every registered field and rely on documentation. Rejected because it creates security expectations that runtime services do not satisfy.

### Guard templates are platform configuration, constraints are role context

Guard template creation and global status remain in the authorization center. The role workbench only displays guard requirements attached to actions selected in Function & Menu. State-sensitive results continue to be supplied by owner services during action availability or execution.

### Two validation modes

Actual User mode uses a searchable user selector and evaluates the user's real roles, organization context, and policies. The UI warns when the selected user does not hold the edited role. Current Role Simulation mode sends a role subject plus optional organization context to a dedicated preview endpoint and never persists a user-role assignment.

Simulation responses and actual-user responses share one layered explanation schema: function, menu, data scope, field rules, guard requirements, final decision, reasons, and policy versions.

## Risks / Trade-offs

- [Owner services may falsely advertise field enforcement] → Require manifest contract tests and fail deployment/registration validation when declared adapters are missing.
- [Role simulation could diverge from real-user evaluation] → Use the same decision pipeline and only replace subject resolution; return a prominent `SIMULATION` marker and supplied context.
- [Menu mappings may be incomplete] → Surface unmapped nodes and block “configuration complete” status for active business menus lacking required permission mappings.
- [Large resource trees can overwhelm implementers] → Group by business domain/owner service, support search, and show selected/effective counts.
- [Migration changes familiar navigation] → Preserve existing data and provide a temporary deep link to the authorization-center template administration page.

## Migration Plan

1. Add registry capability metadata, menu derivation diagnostics, role simulation API, and tests without changing the existing page.
2. Add owner-service field adapters and mark only verified capabilities as enforced.
3. Introduce the new workbench behind a feature flag and compare actual-user preview results with the existing decision preview.
4. Make the new workbench default, remove role-local guard-template administration, and retain authorization-center administration.
5. Remove the legacy tab layout after one release. Rollback switches the frontend flag; persisted grants and policies are unchanged.

## Open Questions

- Which non-lowcode owner service will be the first mandatory field-enforcement reference implementation?
- Should simulation accept explicit organization IDs, or select from existing organization assignments only?
- Should an unmapped catalog-only menu be informational while an unmapped business page is blocking?
