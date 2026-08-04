## ADDED Requirements

### Requirement: Repository-wide comment standard
The repository MUST maintain one normative comment standard covering Java, Python, TypeScript/Vue, SQL, YAML, and operational scripts, and all contributor guidance and automated rules SHALL reference that standard as the source of truth.

#### Scenario: Developer needs language-specific guidance
- **WHEN** a developer changes a supported source or configuration file
- **THEN** the standard identifies the required comment format, mandatory semantic content, exemptions, and prohibited comment patterns for that language

### Requirement: Intent-focused comments
Comments MUST explain non-obvious intent, business invariants, constraints, risks, or trade-offs and MUST NOT merely restate syntax or duplicate information that is already clear from names and types.

#### Scenario: Self-explanatory implementation
- **WHEN** code contains a trivial accessor, obvious assignment, or straightforward control flow with clear naming
- **THEN** the code is not required to contain a redundant explanatory comment

#### Scenario: Non-obvious business decision
- **WHEN** an implementation selects behavior because of a business rule, compatibility constraint, or failure-mode trade-off that is not evident from the code
- **THEN** a nearby comment explains the reason and invariant that future changes must preserve

### Requirement: Public contract documentation
Public modules, types, interfaces, APIs, shared functions, DTOs, events, and extension points MUST document their responsibility and caller-relevant contract when the language construct does not already express it completely.

#### Scenario: New public contract is introduced
- **WHEN** a change adds a public contract consumed outside its implementation unit
- **THEN** the contract documents its purpose, important inputs and outputs, error or lifecycle behavior, and compatibility assumptions as applicable

### Requirement: High-risk logic documentation
Code implementing authorization, tenant or organization data scope, security filtering, transaction boundaries, idempotency, concurrency, Temporal determinism, cross-service ownership, AI action governance, compensation, degradation, or complex state transitions MUST document the non-obvious safety invariant at the decision point.

#### Scenario: Authorization scope is evaluated
- **WHEN** code combines role permission, tenant isolation, organization hierarchy, or default-deny behavior
- **THEN** comments explain the security boundary, fallback behavior, and invariant that prevents permission broadening

#### Scenario: Distributed side effect is implemented
- **WHEN** an Activity, event consumer, workflow step, or Global Action performs a retried or distributed side effect
- **THEN** comments identify the idempotency or compensation strategy and any ownership constraint not encoded by the type system

### Requirement: Comment and implementation consistency
Every code change MUST update or remove comments whose claims are affected by that change, and reviewers SHALL treat stale or contradictory comments as a failing defect.

#### Scenario: Existing behavior changes
- **WHEN** a change alters behavior, constraints, exceptions, ordering, or ownership described by an existing comment
- **THEN** the same change updates or removes that comment so it remains consistent with the implementation and tests

### Requirement: Incremental legacy remediation
The repository SHALL maintain an auditable baseline of existing comment debt and SHALL remediate legacy gaps in risk-prioritized waves without requiring unrelated code to be mechanically commented.

#### Scenario: Initial repository scan completes
- **WHEN** the comment governance scanner analyzes the current repository
- **THEN** it produces module-level findings classified by rule and remediation wave while excluding generated, vendored, build, and migration-output directories

#### Scenario: Developer modifies legacy code
- **WHEN** a change touches a legacy public contract or high-risk decision that is present in the debt baseline
- **THEN** the change adds or corrects the required semantic comment within the touched scope before it can pass review

### Requirement: Differential enforcement
CI MUST block new comment-governance violations introduced by changed files while reporting pre-existing baseline violations separately until their remediation wave is complete.

#### Scenario: New violation is introduced
- **WHEN** a pull request adds an undocumented public contract or high-risk decision required by the standard
- **THEN** CI fails with a stable rule identifier, file path, line, and remediation guidance

#### Scenario: Unrelated historical debt remains
- **WHEN** a pull request does not touch an existing baseline violation
- **THEN** CI reports the debt without failing that pull request solely because of the historical violation

### Requirement: Narrow and expiring exemptions
Any suppression of an automated comment rule MUST be narrowly scoped and MUST record the rule identifier, reason, responsible owner, and expiry or removal condition.

#### Scenario: False positive cannot be fixed immediately
- **WHEN** a maintainer suppresses a comment-governance finding
- **THEN** the suppression applies only to the smallest relevant scope and is rejected if required justification or expiry metadata is absent

### Requirement: Comment security and hygiene
Comments MUST NOT contain real credentials, personal data, secrets, exploitable internal security details, personal authorship history, or disabled code retained as comments.

#### Scenario: Sensitive example is needed
- **WHEN** documentation needs to illustrate a sensitive value or identity
- **THEN** it uses an unmistakably synthetic placeholder and does not expose production or personal information

### Requirement: Rule severity classification
Every comment-governance rule MUST declare a stable identifier and one severity of BLOCKER, ADVISORY, or PROHIBITED, and enforcement SHALL follow the severity semantics defined by the repository standard.

#### Scenario: Scanner reports a finding
- **WHEN** an automated or manual check identifies a comment-governance violation
- **THEN** the result includes its rule identifier, severity, location, reason, and actionable remediation guidance

### Requirement: Comment language and placement
Internal business comments SHALL use concise Chinese for business intent while preserving standard protocol names, code identifiers, and externally defined technical terms in their canonical form, and each comment MUST be placed at the smallest scope that owns the described invariant.

#### Scenario: Invariant spans multiple methods
- **WHEN** a constraint applies to every operation of a type or interface
- **THEN** the constraint is documented at type or contract level instead of being duplicated in each method

#### Scenario: Constraint applies to one decision
- **WHEN** a non-obvious rule affects only one branch, fallback, or state transition
- **THEN** the explanation is placed next to that decision point

### Requirement: Temporary annotation lifecycle
TODO, FIXME, compatibility notes, and other temporary comments MUST include a responsible owner, a trackable work item, and a verifiable removal condition or expiry date.

#### Scenario: Temporary workaround is added
- **WHEN** a change introduces a temporary compatibility or workaround comment
- **THEN** CI rejects it unless ownership, tracking, and removal metadata are present

### Requirement: Decision references remain self-contained
Comments MAY reference an OpenSpec change, ADR, issue, or external standard, but MUST summarize the decision reason and preserved invariant without requiring the reference to understand the code safely.

#### Scenario: Architecture decision is referenced
- **WHEN** a comment links to an ADR or OpenSpec artifact
- **THEN** the nearby text still explains the relevant constraint and why the implementation must preserve it

### Requirement: Domain value semantics
Non-obvious database fields, configuration values, feature flags, cache entries, time values, monetary values, pagination contracts, and ordered collections MUST document their applicable unit, range, lifecycle, consistency, ordering, sensitivity, restart, or rollback semantics.

#### Scenario: Operational configuration is introduced
- **WHEN** a non-default configuration or feature flag can affect security, availability, compatibility, or deployment
- **THEN** its documentation states the default, legal values, enabled and disabled behavior, production guidance, restart requirement, and removal or rollback condition as applicable

#### Scenario: Time or money field is introduced
- **WHEN** a contract exposes a time or monetary value whose semantics are not fully expressed by its type
- **THEN** documentation states timezone or time source, or currency, smallest unit, precision, and rounding rule as applicable

#### Scenario: Cache or concurrency mechanism is introduced
- **WHEN** code adds caching, locking, optimistic concurrency, or a lock-free assumption
- **THEN** comments explain key composition, TTL and invalidation, consistency boundary, lock order and timeout, or the invariant that makes the lock-free approach safe as applicable

### Requirement: Event and distributed contract semantics
Domain events and distributed commands MUST document fact timing, ownership, ordering guarantees, duplicate-delivery handling, idempotency identity, compatibility, and compensation expectations that are not encoded in the schema.

#### Scenario: New domain event is published
- **WHEN** a service introduces or changes an event consumed outside the owner service
- **THEN** the event contract explains when the fact becomes true, delivery and ordering guarantees, duplicate handling, and compatibility expectations

### Requirement: Generated source declaration
Generated source files MUST identify their generator and regeneration method, MUST warn against manual editing, and SHALL be excluded from ordinary semantic-comment debt checks.

#### Scenario: Generated file is scanned
- **WHEN** a recognized generated file contains the required generation declaration
- **THEN** the scanner excludes it from public-contract and high-risk implementation comment requirements

### Requirement: Behavioral claims are verified
Comments that promise authorization boundaries, default-deny behavior, validation limits, degradation, idempotency, compensation, ordering, or compatibility MUST be supported by an identifiable automated test or equivalent executable verification.

#### Scenario: Default-deny behavior is documented
- **WHEN** a comment states that an unresolved permission scope returns no data instead of broadening access
- **THEN** a test demonstrates the unresolved-scope behavior and fails if the implementation returns broader data

### Requirement: AI-generated comment accountability
AI-generated comments MUST be reviewed by the change author for factual accuracy and MUST NOT be accepted solely because they satisfy a structural documentation check.

#### Scenario: AI drafts comments for changed code
- **WHEN** generated comments are included in a change
- **THEN** the author and reviewer verify their business claims against implementation, contracts, and tests before approval

### Requirement: Comment cleanup follows code lifecycle
Removing or replacing implementation MUST also remove or update associated comments, TODO items, configuration explanations, and documentation references that are no longer valid.

#### Scenario: Compatibility path is deleted
- **WHEN** a change removes a compatibility branch or feature flag
- **THEN** the same change removes its temporary comments and updates related configuration and rollback guidance
