## ADDED Requirements

### Requirement: Mandatory Owner field enforcement contract
Every Owner that registers authorization fields MUST declare and implement a verified field enforcement adapter for the corresponding resource before field policies can be configured.

#### Scenario: New field-bearing resource is synchronized
- **WHEN** an Owner synchronizes a resource containing field metadata without a matching verified enforcement declaration
- **THEN** the registry rejects enforcement advertisement and marks the resource non-compliant

### Requirement: Read-side field enforcement
Owner APIs SHALL obtain effective field rules and apply hidden or masked read rules to list, detail, export, event projection, and governed tool responses before data leaves the Owner boundary.

#### Scenario: Role masks a phone field
- **WHEN** a user whose role has `USER.phone` configured as `MASKED` reads a user list or detail
- **THEN** every returned phone value is transformed using the configured mask strategy

#### Scenario: Field is hidden
- **WHEN** an effective field rule marks a field as `HIDDEN`
- **THEN** the Owner response omits the field or returns no value according to its declared response contract

### Requirement: Write-side field enforcement
Owner APIs MUST validate submitted registered fields against effective write rules before create, update, import, workflow action, or AI/tool mutation.

#### Scenario: Field write is denied
- **WHEN** a request submits a field whose effective write mode is `DENIED` or `READ_ONLY`
- **THEN** the Owner rejects the mutation without persisting any submitted changes

### Requirement: Fail-closed policy configuration
The authorization registry MUST reject a field policy when the resource or field is inactive, missing, owned inconsistently, or lacks the enforcement capability required by that policy.

#### Scenario: Administrator configures masking on an unready resource
- **WHEN** an administrator saves a masked or hidden read policy for a resource without verified read enforcement
- **THEN** the operation fails with an explicit field-enforcement-not-ready error

### Requirement: Existing resource coverage
All existing active resources that contain registered fields SHALL report verified enforcement readiness, including USER, ORG_UNIT, lowcode forms, and custom document runtime resources.

#### Scenario: Production readiness is evaluated
- **WHEN** the platform runs field-governance diagnostics
- **THEN** no active field-bearing resource is reported as unverified or partially enforced

### Requirement: Coverage diagnostics
The resource registration center SHALL display field-enforcement readiness, supported read/write modes, Owner declaration identity, and actionable non-compliance reasons.

#### Scenario: Owner adapter is missing
- **WHEN** a field-bearing resource lacks a verified Owner adapter
- **THEN** the resource registration center marks it non-compliant and identifies the missing declaration or enforcement side

### Requirement: Contract and architecture gate
CI MUST fail when a new field-bearing resource lacks a verified Owner adapter, bypasses shared field-rule execution, or exposes registered fields through an ungoverned Owner API.

#### Scenario: Developer adds fields without an adapter
- **WHEN** contract tests discover registered field metadata without matching read/write enforcement coverage
- **THEN** the build fails before merge
