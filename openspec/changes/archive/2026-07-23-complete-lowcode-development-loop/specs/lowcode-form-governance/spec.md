## ADDED Requirements

### Requirement: Form publication exposes relation-ready immutable snapshots
Published form snapshots SHALL expose stable field metadata needed to validate parent keys, child foreign keys, nested editors, and field authorization without permitting published schemas to be mutated.

#### Scenario: Application binds published forms
- **WHEN** an application draft selects master, child, and grandchild form definition ids
- **THEN** relation validation resolves immutable field metadata from the exact published versions

### Requirement: Publishing a referenced form remains independent
Publishing a form SHALL register its own authorization resource and SHALL NOT automatically create an application or user navigation entry.

#### Scenario: Publish form definition
- **WHEN** a designer publishes a valid leave form
- **THEN** the form becomes selectable by application drafts and registers `LOWCODE_FORM:<FORM_KEY>` while remaining absent from end-user application navigation until an application is published
