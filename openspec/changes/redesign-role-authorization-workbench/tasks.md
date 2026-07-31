## 1. Authorization contracts and registry metadata

- [x] 1.1 Extend authorization resource manifests and DTOs with read-hide, read-mask, and write-deny enforcement capability flags.
- [x] 1.2 Persist or derive verified field-enforcement capability metadata in the authorization registry and expose it in resource-tree/options APIs.
- [x] 1.3 Add menu mapping diagnostics that resolve permission codes to registered resource/actions and classify missing or invalid mappings.
- [x] 1.4 Extend role menu projection responses with `DIRECT_GRANT` and `ANCESTOR` derivation evidence.
- [x] 1.5 Add backend unit and contract tests for capability metadata and menu derivation diagnostics.

## 2. Role simulation and explainable preview

- [x] 2.1 Define a tenant-scoped role-simulation preview request that accepts role ID and optional organization context without creating assignments.
- [x] 2.2 Reuse the production authorization decision pipeline with simulated subject resolution and mark responses as `SIMULATION`.
- [x] 2.3 Extend preview output with menu derivation, layered function/data/field/guard explanations, and policy versions.
- [x] 2.4 Enforce preview permissions and tenant boundaries for actual-user and role-simulation modes.
- [x] 2.5 Add tests comparing actual-user and equivalent role-simulation decisions, including cross-tenant denial.

## 3. Runtime field-policy enforcement

- [x] 3.1 Define reusable owner-service read filtering/masking and write validation adapters for authorization field rules.
- [x] 3.2 Update low-code manifests to advertise the field capabilities already enforced by the low-code runtime and cover them with contract tests.
- [x] 3.3 Implement the first non-lowcode owner-service reference adapter for list/detail reads and create/update writes.
- [x] 3.4 Add fail-closed tests for hidden reads, masked reads, denied writes, and bypassing frontend controls.
- [x] 3.5 Prevent resources without verified adapters from advertising field enforcement capabilities.

## 4. Guided role authorization workbench

- [x] 4.1 Split the role authorization UI into reusable stage components and add ordered stage status navigation.
- [x] 4.2 Implement Function & Menu with function selection, live derived menu preview, ancestor evidence, reverse mapping, and unmapped warnings.
- [x] 4.3 Implement Data Scope as resource/action, scope/dimension, organization selection, effective summary, and policy evidence sections.
- [x] 4.4 Implement Field Access with enforcement capability badges and disable unsupported read/write rule combinations.
- [x] 4.5 Replace role-local guard-template administration with a read-only Business Constraints stage and link to authorization-center administration.
- [x] 4.6 Implement Validation with searchable actual-user selection, role-membership warning, current-role simulation, and layered decision results.
- [x] 4.7 Preserve existing grants and policies while removing independent writable menu state from the role workflow.

## 5. Verification and rollout

- [x] 5.1 Add frontend tests for stage navigation, menu derivation explanations, data-scope summaries, capability gating, and both validation modes.
- [x] 5.2 Add end-to-end acceptance scenarios for an allowed role with organization scope, masked fields, and required guards.
- [x] 5.3 Add an end-to-end denied scenario covering unmapped menus, absent grants, and unsupported field enforcement.
- [x] 5.4 Release the workbench behind a feature flag and compare preview outcomes with the legacy page.
- [x] 5.5 Document implementation-person workflows, field-enforcement ownership, migration, rollback, and troubleshooting.
- [ ] 5.6 Remove the legacy role authorization tab layout after the compatibility period and successful production verification.
