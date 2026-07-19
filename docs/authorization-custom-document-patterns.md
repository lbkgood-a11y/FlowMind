# Custom Document Authorization Patterns

This guide describes how handwritten business services integrate with TrioBase enterprise authorization without creating fake menu entries.

## Manifest

Each handwritten service declares stable document resources through `CustomDocumentAuthorizationManifest`.

```json
{
  "tenantId": "tenant-a",
  "serviceName": "service-contract",
  "documents": [
    {
      "code": "CUSTOM_DOC:CONTRACT",
      "documentType": "CONTRACT",
      "displayName": "合同单据",
      "actions": [
        { "actionCode": "VIEW" },
        { "actionCode": "EDIT", "guardCodes": ["DOCUMENT_STATUS", "ARCHIVED_LOCK"] },
        { "actionCode": "APPROVE", "guardCodes": ["WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS"] },
        { "actionCode": "EXPORT" }
      ],
      "fields": [
        { "fieldKey": "amount", "fieldLabel": "合同金额", "fieldType": "number", "sensitivityClassification": "FINANCIAL", "defaultMaskStrategy": "LAST4" }
      ],
      "guards": [
        { "guardCode": "DOCUMENT_STATUS", "ownerService": "service-contract", "supportedResourceTypes": "CUSTOM_DOC" }
      ]
    }
  ]
}
```

Rules:

- Resource codes use `CUSTOM_DOC:<DOCUMENT_TYPE>` and remain stable across releases.
- Actions use canonical product language: `VIEW`, `CREATE`, `EDIT`, `DELETE`, `SUBMIT`, `APPROVE`, `REJECT`, `EXPORT`.
- Field keys are DTO/business field keys, not database column names.
- Guard templates describe business checks; services evaluate them locally.

## Synchronization

Services synchronize manifests to `service-auth` through the internal resource sync API before enabling protected runtime traffic. The reference implementation is:

- [CustomDocumentAuthorizationSyncClient](/Users/lv/workspace/flowmind/FlowMind/trio-base-services/service-openapi/src/main/java/com/triobase/service/openapi/service/authorization/CustomDocumentAuthorizationSyncClient.java)
- [CustomDocumentAuthorizationStartupSync](/Users/lv/workspace/flowmind/FlowMind/trio-base-services/service-openapi/src/main/java/com/triobase/service/openapi/service/authorization/CustomDocumentAuthorizationStartupSync.java)

Enable startup sync with:

```properties
triobase.authorization.custom-doc.sync-enabled=true
triobase.authorization.custom-doc.tenant-id=tenant-a
```

## Enforcement

Service endpoints call the decision API with the authenticated tenant and user:

```text
resourceCode = CUSTOM_DOC:CONTRACT
actionCode   = EXPORT
ownerService = service-contract
fieldKeys    = requested DTO fields
```

The service allows the operation only when the central decision allows it and every local guard passes.

## Data Scope

Compile returned data scopes into service-owned predicates:

- `ALL`: tenant predicate only.
- `SELF`: `owner_user_id = currentUser` or equivalent submitter column.
- `OWN_ORG` / `OWN_ORG_AND_CHILDREN` / `ASSIGNED_ORGS`: filter by resolved org ids against explicit owner org columns.
- Unknown or unsupported scopes: return no records or deny the operation.

Never treat an organization scope as `ALL` unless the service has concrete ownership metadata to filter on.

## Field Rules

Apply field rules before DTO serialization and before accepting mutations:

- `HIDDEN`: omit the field from response DTOs.
- `MASKED`: replace the value with the configured mask strategy.
- `READ_ONLY` or `DENIED`: reject writes when the request contains that field.

Do not log raw sensitive field values in decision diagnostics; log field keys and policy outcomes only.

## Local Guards

Keep domain checks inside the owning service:

- `DOCUMENT_STATUS`: status must permit the operation.
- `ARCHIVED_LOCK`: archived documents cannot be edited or submitted.
- `WORKFLOW_CANDIDATE`: workflow service confirms the user owns or can claim the pending task.
- `NO_SELF_APPROVAL`: submitter cannot approve their own document.

Return `AuthzGuardResult` for failed guards so lowcode descriptors and admin diagnostics can explain the denial.
