# Enterprise Authorization Rollout

This document covers the first production rollout of TrioBase enterprise authorization for menu-compatible RBAC, lowcode resources, workflow guards, and handwritten document manifests.

## Readiness Checklist

- Apply `service-auth` migration `V54__enterprise_authorization_model.sql`.
- Apply `service-lowcode` migration `V8__lowcode_field_authorization_metadata.sql`.
- Verify gateway token validation propagates the real tenant id and authorization versions from `service-auth`.
- Verify `service-auth` internal endpoints are reachable only by trusted services:
  - `POST /internal/v1/authz/decide`
  - `POST /internal/v1/authz/batch-decide`
  - `POST /internal/v1/authz/resources/sync`
- Publish one lowcode form and one lowcode runtime application in a non-production tenant, then confirm resources appear in `GET /api/v1/authz/resources/tree`.
- For handwritten services, set `triobase.authorization.custom-doc.sync-enabled=true` only after the service manifest is reviewed.

## Rollout Order

1. Deploy `service-auth` first so registry, grants, field policies, guard templates, and decision APIs exist.
2. Deploy `platform-gateway` so real tenant and authorization version headers are propagated.
3. Deploy `service-lowcode`; newly published forms and apps synchronize resources before becoming runtime-visible.
4. Publish or republish lowcode forms and applications to populate resources, actions, fields, and guard metadata.
5. Deploy handwritten services with custom document manifests. Start with `triobase.authorization.custom-doc.sync-enabled=false`, then enable per service after validation.
6. Synchronize and activate the page capability catalog, then inspect readiness in the administrator-only mapping diagnostic tab.
7. Keep the tenant in `MIGRATION`, generate review-required drafts, and resolve every partial, ambiguous, or unmapped legacy grant.
8. Use the role authorization workbench to select page access, read capabilities, business operations, scopes, verified field rules, and constraints.
9. Validate with actual users and the current online role, publish immutable releases, and confirm decision equivalence.
10. Switch the tenant to `PAGE_CAPABILITY` only after zero unintended permission expansion is confirmed; legacy role-grant writes are then blocked.

## Production Acceptance Gate

Use the administrator **上线验收** tab before cutover. `GET /api/v1/authz/compatibility-dashboard`
recalculates its result from the active catalog, active role releases, immutable compiled
evidence, current runtime grants, migration reviews, drift records, publication failures,
and rollbacks. The screen is evidence, not a manually editable checklist.

Cutover is allowed only when all of the following are true:

- every active page capability is `READY`;
- every enabled role has an active published release;
- each role's current runtime grants exactly match its active release evidence;
- no runtime grant exists outside the active release (zero unintended expansion);
- every detected migration expansion has been explicitly reviewed;
- no mapping drift remains open.

`PUT /api/v1/authz/management-mode?mode=PAGE_CAPABILITY` repeats the same server-side
assessment atomically. A disabled button cannot be bypassed by calling the API directly.
After successful cutover, switching back to `LEGACY` or `MIGRATION` is rejected so two
management paths cannot write permissions concurrently.

The acceptance contract also verifies read-only roles, create without history-read,
separate read/operation scopes, owner-side approval guards, field masking and denied writes,
export denial, stale projection rejection, direct API bypass, and tenant boundaries.

## Compatibility Rules

- Menu navigation is projected from the active role release; page access, read, and operations remain distinct business intents.
- `sys_auth_grant` remains the runtime enforcement fact, while immutable page-capability intent and compiled evidence are the management and audit facts.
- `sys_menu.permission_code` is used only to project menu visibility from grants.
- Existing `@RequirePermission` and `@RequireDataScope` declarations remain valid code-level guards, but they do not create a second grant source.
- Lowcode runtime must use registered resource/action codes and fail closed when a resource/action is missing.
- Explicit deny grants take precedence over allow grants.
- Services must treat unsupported data-scope results as no access.

## Rollback

- Keep the database migrations in place; V60/V64/V65 are required for the single authorization source.
- Disable custom document startup sync with `triobase.authorization.custom-doc.sync-enabled=false`.
- Restore the last known-good immutable role release if a role receives too much access; never reconstruct rollback from current mappings.
- If a published lowcode resource sync is wrong, fix metadata and republish; resource sync is idempotent.
- If decision API availability is degraded, document operations should fail closed; do not reintroduce legacy permission fallback.

## Operational Signals

- `AUTHZ_RESOURCE_ACTION_NOT_REGISTERED`: resource sync or migration is missing.
- `AUTHZ_GRANT_NOT_FOUND`: role has no matching function grant.
- `AUTHZ_DENY_GRANT_MATCHED`: explicit deny is working as designed.
- `AUTHZ_FIELD_DENY_POLICY`: a field policy is hiding and blocking the field.
- `AUTHZ_FUNCTION_DENIED`: field default became hidden because function access was denied.
