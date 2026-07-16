## ADDED Requirements

### Requirement: Define directional field mappings
The system SHALL define versioned request and response mapping sets between pinned canonical and external structure versions using explicit source and target field paths.

#### Scenario: Map nested request fields
- **WHEN** a mapping set maps canonical nested fields to valid external target paths
- **THEN** the system validates and stores the mapping as a draft without modifying either source structure

#### Scenario: Reference an unknown path
- **WHEN** a mapping rule references a field path absent from its pinned structure version
- **THEN** the system rejects validation with the invalid rule and path identified

### Requirement: Provide constrained transformation operations
The system SHALL provide allow-listed mapping operations and MUST NOT execute arbitrary scripts, expressions, or dynamically loaded code supplied through mapping metadata.

#### Scenario: Apply registered type conversion
- **WHEN** a mapping requests a registered compatible type converter
- **THEN** the transformation applies the converter and validates the result against the target field

#### Scenario: Submit arbitrary expression
- **WHEN** a mapping definition contains JavaScript, SpEL, OGNL, or an unregistered operation
- **THEN** the system rejects the definition before publication

### Requirement: Manage field value mappings
The system SHALL manage reusable versioned value-map sets with forward and reverse values, case policy, optional default, and unmapped-value behavior.

#### Scenario: Map a known enumeration value
- **WHEN** an input value has a matching entry in the pinned value-map version
- **THEN** the system emits the configured target value in the requested direction

#### Scenario: Fail on unmapped value
- **WHEN** a value has no mapping and the policy is `FAIL`
- **THEN** transformation stops with a sanitized error identifying the field and value-map set

#### Scenario: Use configured default
- **WHEN** a value has no mapping and the policy is `USE_DEFAULT`
- **THEN** the configured default target value is emitted

### Requirement: Validate mapping coverage
The system SHALL calculate mapping coverage for required target fields and block publication when a required target field cannot be produced.

#### Scenario: Required target is uncovered
- **WHEN** a required target field has no source mapping, constant, or default
- **THEN** publication is blocked with a required-field coverage error

### Requirement: Preview and test transformations
The system SHALL allow authorized users to preview draft transformations with sample payloads and save contract test cases with expected output or expected failure.

#### Scenario: Preview valid payload
- **WHEN** a user previews a mapping with a payload valid against the pinned source schema
- **THEN** the system returns the transformed payload, applied-rule trace, warnings, and target-schema validation result without invoking an external endpoint

#### Scenario: Run publication tests
- **WHEN** a mapping release is prepared for publication
- **THEN** all enabled stored contract tests run and any required failing test blocks publication

### Requirement: Protect sensitive mapping data
The system SHALL classify sensitive fields, redact their values from previews and logs, and prohibit credentials or authorization headers from being stored as mapping constants.

#### Scenario: Preview sensitive field mapping
- **WHEN** a sample payload contains a field classified as sensitive
- **THEN** transformation can use the value but preview traces and persisted logs display only a redacted representation

