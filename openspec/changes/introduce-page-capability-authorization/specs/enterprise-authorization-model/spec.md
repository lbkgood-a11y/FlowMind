## MODIFIED Requirements

### Requirement: Authorization grants are the single function authorization source
The system SHALL store published role management intent as versioned page capability assignments and SHALL compile each active release into `sys_auth_grant` as the single runtime function authorization source; menu membership SHALL remain a read-only projection derived from compiled grants and menu metadata.

#### Scenario: Resource grant without menu
- **WHEN** a published page capability compiles `CUSTOM_DOC:CONTRACT:EXPORT` without any corresponding menu row
- **THEN** authorization decisions for contract export can allow the role based on the compiled resource grant while the release retains the originating business capability evidence

#### Scenario: Menu visibility is projected from grants
- **WHEN** a role's active compiled allow grant matches menu permission metadata
- **THEN** dynamic routes can show the corresponding menu and ancestors without writing a separate role-menu authorization row

#### Scenario: Draft intent is not yet published
- **WHEN** an implementation person changes a role's page capability draft without publishing it
- **THEN** `sys_auth_grant`, dynamic routes, and runtime decisions continue to use the previous active release

#### Scenario: Published mapping is missing at runtime
- **WHEN** an owner service receives a request for a resource/action absent from the role's active compiled grants
- **THEN** the runtime denies the action and does not infer permission directly from page capability intent

## ADDED Requirements

### Requirement: Compiled authorization preserves release evidence
Every grant and policy projection generated from page capability intent SHALL reference the tenant, role, release, catalog version, originating capability, and compilation evidence needed for explanation and rollback.

#### Scenario: Explain an operation grant
- **WHEN** an administrator inspects why a role can disable a user
- **THEN** the system traces the runtime grant to the published User Management disable-user capability, its dependencies, mapping version, and release actor

### Requirement: Compilation and runtime remain fail-closed
Authorization compilation SHALL reject unknown, inactive, unsupported, cyclic, or cross-tenant capability mappings, and runtime enforcement SHALL deny missing or inconsistent compiled evidence.

#### Scenario: Capability targets an inactive action
- **WHEN** a draft includes a capability whose required target action is inactive
- **THEN** validation blocks publication and the previous active release remains effective

