## Context

The authorization service stores field policies and can calculate field rules, while `FieldMaskHelper` provides shared masking and write validation. Lowcode and the reference custom-document runtime consume those rules, but USER and ORG_UNIT currently expose registered field metadata without runtime enforcement. The registry validates advertised enforcement only during resource synchronization and still accepts policies for resources that do not advertise verified enforcement.

## Goals / Non-Goals

**Goals:**

- Make field authorization a global contract with Owner-local enforcement at every API boundary.
- Fail closed when a field-bearing resource has no verified enforcement declaration.
- Bring every existing field-bearing resource to verified production readiness.
- Expose coverage diagnostics and enforce the same contract for future resources.

**Non-Goals:**

- Applying arbitrary JSON rewriting in the gateway.
- Treating every API response property as authorization-configurable without an Owner manifest.
- Moving business facts or serialization into the authorization service.

## Decisions

### 1. Owner services enforce centrally calculated rules

The authorization service remains the policy decision point; each Owner remains the policy enforcement point. Owners request a decision for their resource/action and registered fields, then apply shared read/write helpers before returning data or accepting changes. This preserves business ownership and works for nested DTOs, exports, and tool contracts.

### 2. Introduce a verified adapter declaration contract

Each Owner declares resource code/type, supported read/write operations, and field mappings through a typed `FieldEnforcementManifest`. The authorization registry materializes this declaration into resource enforcement flags. Hard-coded allowlists are replaced by declared beans/sync metadata plus contract tests.

### 3. Block policies for non-ready resources

Saving a field policy requires an active resource, registered field, and verified enforcement flags compatible with the requested read/write modes. Masked/hidden reads require read enforcement; read-only/denied writes require write enforcement. The API fails with an explicit readiness error instead of accepting a policy that cannot work.

### 4. Provide typed adapters for existing DTOs

USER and ORG_UNIT receive Owner-local adapters that map DTOs to field maps, apply common rules, and reconstruct safe DTOs. Existing lowcode map-based enforcement and custom-document adapters are retained but tested against the same contract.

### 5. Treat lists, detail, writes, exports, and tools consistently

Owner service application methods apply the same field decision to each returned item and validate only submitted fields on mutation. Governed exports and AI tools must call the same Owner application service, not bypass it with direct persistence access.

### 6. Diagnose coverage from registry facts

The resource catalog reports field count, advertised read/write enforcement, verified declaration, and readiness. Any active resource with fields but incomplete enforcement is non-compliant and blocks production readiness checks.

## Risks / Trade-offs

- [Per-request decision calls add latency] → Use an in-process decision service for service-auth and a version-keyed short-lived decision cache/client for remote Owners.
- [Masking DTOs can accidentally lose non-governed fields] → Typed adapters copy the full DTO then override/remove only registered fields, with contract tests.
- [Admin users may expect bypass] → Field rules apply to all roles, including ADMIN; explicit policies determine visibility rather than implicit bypass.
- [Existing clients expect field presence] → MASKED preserves the property, HIDDEN omits it where response representation supports omission; typed legacy DTOs use null plus non-null serialization controls only where contract-compatible.
- [Incorrect Owner metadata] → Migrate ORG_UNIT ownership to `service-org` and validate manifest/resource Owner equality.

## Migration Plan

1. Add shared manifest/adapter and readiness contracts.
2. Harden registry policy writes and expose coverage diagnostics.
3. Integrate USER, then ORG_UNIT, and run contract tests for lowcode/custom documents.
4. Migrate enforcement flags and correct Owner metadata only after adapters are deployed.
5. Enable strict readiness gate after all current field-bearing resources report READY.

## Open Questions

- None.
