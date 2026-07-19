## MODIFIED Requirements

### Requirement: Operate lifecycle assets from the console
The system SHALL provide functional internal pages that invoke typed management APIs for create, inspect, draft update, publish, deprecate, archive, activate, rollback, suspend, reactivate, revoke, approve, upgrade, and policy publication where those actions apply, and those pages SHALL default to visual function-specific configuration instead of raw JSON payload entry.

#### Scenario: Publish a route release
- **WHEN** an authorized operator validates a route version and publishes and activates its release from the route page
- **THEN** the console shows the immutable release identifier and updated active-release state without permitting a runtime URL override

#### Scenario: Onboard an application
- **WHEN** an authorized operator creates an application, creates an environment client, binds or rotates a credential reference, and activates the client
- **THEN** the console displays the resulting lifecycle state while revealing generated secret material only in the one-time response

#### Scenario: Create lifecycle asset visually
- **WHEN** an authorized implementation operator opens a lifecycle create dialog
- **THEN** the console renders visual controls for that asset type, generates the typed backend payload, and offers advanced JSON only as a fallback preview/edit mode
