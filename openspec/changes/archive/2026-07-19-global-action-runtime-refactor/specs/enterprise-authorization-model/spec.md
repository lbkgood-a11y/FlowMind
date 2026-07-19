## MODIFIED Requirements

### Requirement: Authorization decisions evaluate grants fail-closed
The system SHALL expose internal single and batch authorization decision APIs that evaluate tenant boundary, subject roles, direct grants, registered resources, action grants, deny precedence, data scope, field rules, required guard templates, and Global Action context.

#### Scenario: Allow granted action
- **WHEN** a user has an active role grant for resource `LOWCODE_FORM:EXPENSE` and action `APPROVE`
- **THEN** the decision API returns an allowed function decision with the matched grant, resource, action, tenant, subject, Global Action context, and policy versions

#### Scenario: Deny unknown resource
- **WHEN** a caller requests a decision for an unregistered resource or unregistered Global Action target
- **THEN** the decision API returns denied with a fail-closed reason and does not infer permission from request path alone

#### Scenario: Deny takes precedence
- **WHEN** one matching grant allows an action and another active matching grant denies the same action
- **THEN** the decision API returns denied and includes the deny grant in the explanation

#### Scenario: Batch action decision
- **WHEN** a runtime page requests decisions for multiple document actions, fields, and Global Action definitions
- **THEN** the batch decision API returns one stable decision entry per requested resource/action without requiring multiple network round trips

### Requirement: Domain guards compose with central decisions
The system SHALL model domain guards as structured guard requirements owned by the business service and SHALL NOT require `service-auth` to call domain services for state-sensitive checks; Global Action execution MUST compose central decisions with owner-service guard results before mutation.

#### Scenario: Workflow task guard succeeds
- **WHEN** a central decision allows `APPROVE` and the workflow service confirms the task is pending and assigned or candidate to the current user
- **THEN** the final Global Action authorization result allows the approval action

#### Scenario: Self-approval guard fails
- **WHEN** a central decision allows `APPROVE` but the document guard detects the current user is the submitter and self-approval is disallowed
- **THEN** the final Global Action authorization result denies the action and includes the failed guard reason

#### Scenario: Archived document guard fails
- **WHEN** a central decision allows `EDIT` but the owning service detects the document is archived
- **THEN** the final Global Action authorization result denies editing without changing resource grants

### Requirement: Decisions are explainable and auditable
The system SHALL provide explainable decision output and record auditable authorization decisions for protected business operations, including the Global Action identity and bounded action context when enforcement happens through the Action Runtime.

#### Scenario: Explain denied decision
- **WHEN** an administrator previews why a user cannot export a form
- **THEN** the system returns the evaluated subject, resource, action, Global Action type when available, grants, data scope, field rules, guard requirements, and denial reasons without exposing unrelated tenant data

#### Scenario: Audit enforced decision
- **WHEN** a protected document operation is evaluated in enforcement mode through Global Action
- **THEN** the system records action id, actor, tenant, resource, action, allowed flag, reasons, policy versions, trace id, and owner service metadata

#### Scenario: Avoid sensitive payload logging
- **WHEN** a decision references form or document fields from an action payload
- **THEN** the audit log stores field keys and policy results but not raw sensitive field values
