## ADDED Requirements

### Requirement: Menu authorization mappings expose derivation diagnostics
The menu management capability SHALL expose each navigation node's normalized resource/action mapping and SHALL report whether it can participate in role menu derivation.

#### Scenario: Valid mapped business menu
- **WHEN** a business menu has a valid permission code backed by a registered resource action
- **THEN** management and role clients receive the normalized mapping and a valid diagnostic status

#### Scenario: Invalid or missing mapping
- **WHEN** an active business menu lacks a permission code or references an unregistered resource action
- **THEN** management and role clients receive an unmapped diagnostic with an actionable reason

### Requirement: Derived menu results include evidence
The menu projection for a role SHALL identify whether each returned node is directly matched by a function grant or included only as an ancestor.

#### Scenario: Ancestor included for navigation
- **WHEN** a directly granted page requires parent catalogs to form a route
- **THEN** the projection marks the page `DIRECT_GRANT` and its added catalogs `ANCESTOR`
