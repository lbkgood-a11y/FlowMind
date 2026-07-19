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
6. Use the role authorization drawer to grant function actions, data ranges, field rules, and preview decisions for sample users.
7. Watch decision logs for deny spikes, unknown-resource reasons, and unresolved data-scope results.

## Compatibility Rules

- Menu navigation remains separate from function authorization.
- `sys_auth_grant` is the only stored function authorization fact.
- `sys_menu.permission_code` is used only to project menu visibility from grants.
- Existing `@RequirePermission` and `@RequireDataScope` declarations remain valid code-level guards, but they do not create a second grant source.
- Lowcode runtime must use registered resource/action codes and fail closed when a resource/action is missing.
- Explicit deny grants take precedence over allow grants.
- Services must treat unsupported data-scope results as no access.

## Rollback

- Keep the database migrations in place; V60/V64/V65 are required for the single authorization source.
- Disable custom document startup sync with `triobase.authorization.custom-doc.sync-enabled=false`.
- Revoke new role grants or field policies from the authorization drawer if a role receives too much access.
- If a published lowcode resource sync is wrong, fix metadata and republish; resource sync is idempotent.
- If decision API availability is degraded, document operations should fail closed; do not reintroduce legacy permission fallback.

## Operational Signals

- `AUTHZ_RESOURCE_ACTION_NOT_REGISTERED`: resource sync or migration is missing.
- `AUTHZ_GRANT_NOT_FOUND`: role has no matching function grant.
- `AUTHZ_DENY_GRANT_MATCHED`: explicit deny is working as designed.
- `AUTHZ_FIELD_DENY_POLICY`: a field policy is hiding and blocking the field.
- `AUTHZ_FUNCTION_DENIED`: field default became hidden because function access was denied.
