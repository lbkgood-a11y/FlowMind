INSERT INTO oa_structure (
    id, tenant_id, namespace, structure_key, structure_kind, data_format, direction,
    owner_type, owner_id, display_name, description, lifecycle_state,
    created_by, updated_by
) VALUES
    ('OA_STR_LEAVE_PUBLIC', 'default', 'openapi.leave', 'leave.submit.public',
     'EXTERNAL', 'JSON', 'REQUEST', 'SERVICE', 'service-openapi',
     'Leave submit public request', 'Business-facing leave request payload for the OpenAPI leave.submit route.',
     'ACTIVE', 'SYSTEM', 'SYSTEM'),
    ('OA_STR_LEAVE_ACTION', 'default', 'openapi.leave', 'leave.submit.action',
     'CANONICAL', 'JSON', 'REQUEST', 'SERVICE', 'service-openapi',
     'Leave owner action payload', 'Prepared lowcode Global Action payload for submitAndLaunch.',
     'ACTIVE', 'SYSTEM', 'SYSTEM')
ON CONFLICT DO NOTHING;

INSERT INTO oa_structure_version (
    id, structure_id, version_number, compatibility_line, lifecycle_state,
    schema_content, schema_hash, change_summary, semantic_change, compatibility_result,
    published_by, published_at, created_by, updated_by
) VALUES
    ('OA_SV_LEAVE_PUBLIC_1', 'OA_STR_LEAVE_PUBLIC', 1, 1, 'PUBLISHED',
     $json$
     {
       "type": "object",
       "required": ["requestId", "applicant", "leaveType", "startDate", "endDate", "reason"],
       "additionalProperties": false,
       "properties": {
         "requestId": {"type": "string", "minLength": 1, "maxLength": 128},
         "applicant": {"type": "string", "minLength": 1, "maxLength": 128},
         "leaveType": {"type": "string", "minLength": 1, "maxLength": 64},
         "startDate": {"type": "string", "format": "date"},
         "endDate": {"type": "string", "format": "date"},
         "reason": {"type": "string", "minLength": 1, "maxLength": 1024}
       }
     }
     $json$::jsonb,
     '1111111111111111111111111111111111111111111111111111111111111111',
     'Initial leave submit public request schema.',
     '{}'::jsonb, '{"compatible":true}'::jsonb,
     'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
    ('OA_SV_LEAVE_ACTION_1', 'OA_STR_LEAVE_ACTION', 1, 1, 'PUBLISHED',
     $json$
     {
       "type": "object",
       "required": ["requestId", "actionPayload"],
       "additionalProperties": false,
       "properties": {
         "requestId": {"type": "string", "minLength": 1, "maxLength": 128},
         "actionPayload": {
           "type": "object",
           "required": ["appKey", "actionCode", "data"],
           "additionalProperties": false,
           "properties": {
             "appKey": {"const": "leave"},
             "actionCode": {"const": "submitAndLaunch"},
             "data": {
               "type": "object",
               "required": ["applicant", "leaveType", "startDate", "endDate", "reason"],
               "additionalProperties": false,
               "properties": {
                 "applicant": {"type": "string", "minLength": 1, "maxLength": 128},
                 "leaveType": {"type": "string", "minLength": 1, "maxLength": 64},
                 "startDate": {"type": "string", "format": "date"},
                 "endDate": {"type": "string", "format": "date"},
                 "reason": {"type": "string", "minLength": 1, "maxLength": 1024}
               }
             }
           }
         }
       }
     }
     $json$::jsonb,
     '2222222222222222222222222222222222222222222222222222222222222222',
     'Initial leave submit owner action payload schema.',
     '{}'::jsonb, '{"compatible":true}'::jsonb,
     'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM')
ON CONFLICT DO NOTHING;

INSERT INTO oa_mapping_set (
    id, tenant_id, mapping_key, display_name, description, direction,
    canonical_structure_id, external_structure_id, owner_id, lifecycle_state,
    created_by, updated_by
) VALUES (
    'OA_MAP_LEAVE_PUBLIC_ACTION', 'default', 'leave.submit.public-to-action',
    'Leave public request to owner action payload',
    'Maps the public leave submit payload into the lowcode owner action payload.',
    'EXTERNAL_TO_CANONICAL', 'OA_STR_LEAVE_ACTION', 'OA_STR_LEAVE_PUBLIC',
    'service-openapi', 'ACTIVE', 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_mapping_version (
    id, mapping_set_id, version_number, source_structure_version_id, target_structure_version_id,
    lifecycle_state, coverage_result, compiled_plan, compiled_plan_hash,
    published_by, published_at, created_by, updated_by
) VALUES (
    'OA_MAPV_LEAVE_PUBLIC_1', 'OA_MAP_LEAVE_PUBLIC_ACTION', 1,
    'OA_SV_LEAVE_PUBLIC_1', 'OA_SV_LEAVE_ACTION_1',
    'PUBLISHED', '{"coverage":1.0,"seeded":true}'::jsonb,
    $json$
    {
      "formatVersion": "1",
      "mappingVersionId": "OA_MAPV_LEAVE_PUBLIC_1",
      "sourceStructureVersionId": "OA_SV_LEAVE_PUBLIC_1",
      "targetStructureVersionId": "OA_SV_LEAVE_ACTION_1",
      "rules": [
        {"order": 1, "operation": "COPY", "sourcePointer": "/requestId", "targetPointer": "/requestId", "required": true, "config": {}},
        {"order": 2, "operation": "CONSTANT", "targetPointer": "/actionPayload/appKey", "required": true, "config": {"value": "leave"}},
        {"order": 3, "operation": "CONSTANT", "targetPointer": "/actionPayload/actionCode", "required": true, "config": {"value": "submitAndLaunch"}},
        {"order": 4, "operation": "COPY", "sourcePointer": "/applicant", "targetPointer": "/actionPayload/data/applicant", "required": true, "config": {}},
        {"order": 5, "operation": "COPY", "sourcePointer": "/leaveType", "targetPointer": "/actionPayload/data/leaveType", "required": true, "config": {}},
        {"order": 6, "operation": "COPY", "sourcePointer": "/startDate", "targetPointer": "/actionPayload/data/startDate", "required": true, "config": {}},
        {"order": 7, "operation": "COPY", "sourcePointer": "/endDate", "targetPointer": "/actionPayload/data/endDate", "required": true, "config": {}},
        {"order": 8, "operation": "COPY", "sourcePointer": "/reason", "targetPointer": "/actionPayload/data/reason", "required": true, "config": {}}
      ]
    }
    $json$::jsonb,
    '3333333333333333333333333333333333333333333333333333333333333333',
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_mapping_rule (
    id, mapping_version_id, rule_order, operation_type, source_pointer,
    target_pointer, operation_config, required_rule, created_by, updated_by
) VALUES
    ('OA_MAPR_LEAVE_01', 'OA_MAPV_LEAVE_PUBLIC_1', 1, 'COPY', '/requestId', '/requestId', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_02', 'OA_MAPV_LEAVE_PUBLIC_1', 2, 'CONSTANT', NULL, '/actionPayload/appKey', '{"value":"leave"}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_03', 'OA_MAPV_LEAVE_PUBLIC_1', 3, 'CONSTANT', NULL, '/actionPayload/actionCode', '{"value":"submitAndLaunch"}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_04', 'OA_MAPV_LEAVE_PUBLIC_1', 4, 'COPY', '/applicant', '/actionPayload/data/applicant', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_05', 'OA_MAPV_LEAVE_PUBLIC_1', 5, 'COPY', '/leaveType', '/actionPayload/data/leaveType', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_06', 'OA_MAPV_LEAVE_PUBLIC_1', 6, 'COPY', '/startDate', '/actionPayload/data/startDate', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_07', 'OA_MAPV_LEAVE_PUBLIC_1', 7, 'COPY', '/endDate', '/actionPayload/data/endDate', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM'),
    ('OA_MAPR_LEAVE_08', 'OA_MAPV_LEAVE_PUBLIC_1', 8, 'COPY', '/reason', '/actionPayload/data/reason', '{}'::jsonb, TRUE, 'SYSTEM', 'SYSTEM')
ON CONFLICT DO NOTHING;

INSERT INTO oa_orchestration_definition (
    id, tenant_id, orchestration_key, display_name, owner_id,
    lifecycle_state, created_by, updated_by
) VALUES (
    'OA_ORCH_LEAVE_SUBMIT', 'default', 'leave.submit.owner-action',
    'Leave submit owner-action orchestration', 'service-openapi',
    'ACTIVE', 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_orchestration_version (
    id, orchestration_definition_id, version_number, lifecycle_state,
    definition_schema_version, definition_content, definition_hash, validation_result,
    published_by, published_at, created_by, updated_by
) VALUES (
    'OA_ORCHV_LEAVE_SUBMIT_1', 'OA_ORCH_LEAVE_SUBMIT', 1, 'PUBLISHED',
    '1',
    $json$
    {
      "schemaVersion": "1",
      "start": "prepareActionPayload",
      "steps": [
        {
          "key": "prepareActionPayload",
          "type": "TRANSFORM",
          "mappingVersionId": "OA_MAPV_LEAVE_PUBLIC_1",
          "next": "submitLeave"
        },
        {
          "key": "submitLeave",
          "type": "OWNER_ACTION",
          "ownerService": "service-lowcode",
          "actionType": "lowcode.form.submit",
          "targetType": "LOWCODE_FORM",
          "targetId": "leave",
          "payloadPointer": "/actionPayload",
          "idempotencyKeyPointer": "/requestId",
          "executionMode": "SYNC",
          "outputPointer": "/ownerAction",
          "next": "end"
        },
        {"key": "end", "type": "END"}
      ]
    }
    $json$::jsonb,
    '4444444444444444444444444444444444444444444444444444444444444444',
    '{"valid":true,"seeded":true}'::jsonb,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_route_definition (
    id, tenant_id, route_key, display_name, owner_id,
    lifecycle_state, created_by, updated_by
) VALUES (
    'OA_ROUTE_LEAVE_SUBMIT', 'default', 'leave.submit',
    'Submit leave request', 'service-openapi',
    'ACTIVE', 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_route_version (
    id, route_definition_id, version_number, environment, lifecycle_state,
    priority, enabled, route_predicate, execution_mode, orchestration_version_id,
    published_by, published_at, created_by, updated_by
) VALUES (
    'OA_ROUTEV_LEAVE_SUBMIT_1', 'OA_ROUTE_LEAVE_SUBMIT', 1, 'DEV', 'PUBLISHED',
    100, TRUE, '{}'::jsonb, 'ORCHESTRATED', 'OA_ORCHV_LEAVE_SUBMIT_1',
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_release_snapshot (
    id, tenant_id, environment, route_definition_id, route_version_id, release_number,
    lifecycle_state, pinned_dependencies, snapshot_hash, validation_result,
    release_notes, published_by
) VALUES (
    'OA_REL_LEAVE_SUBMIT_1', 'default', 'DEV', 'OA_ROUTE_LEAVE_SUBMIT',
    'OA_ROUTEV_LEAVE_SUBMIT_1', 1, 'PUBLISHED',
    $json$
    {
      "routePlan": {
        "routeVersionId": "OA_ROUTEV_LEAVE_SUBMIT_1",
        "environment": "DEV",
        "priority": 100,
        "enabled": true,
        "predicate": {},
        "executionMode": "ORCHESTRATED",
        "orchestrationVersionId": "OA_ORCHV_LEAVE_SUBMIT_1"
      },
      "routePlanHash": "5555555555555555555555555555555555555555555555555555555555555555",
      "orchestrationVersionId": "OA_ORCHV_LEAVE_SUBMIT_1",
      "orchestrationHash": "4444444444444444444444444444444444444444444444444444444444444444"
    }
    $json$::jsonb,
    '6666666666666666666666666666666666666666666666666666666666666666',
    '{"valid":true,"seeded":true}'::jsonb,
    'Seed release for leave.submit owner-action OpenAPI flow.', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_active_release (
    route_definition_id, environment, release_snapshot_id, policy_version, activated_by
) VALUES (
    'OA_ROUTE_LEAVE_SUBMIT', 'DEV', 'OA_REL_LEAVE_SUBMIT_1', 1, 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_api_product (
    id, tenant_id, product_key, display_name, owner_id, audience, risk_level, visibility,
    documentation, terms, default_scopes, default_traffic_policy, default_security_policy,
    lifecycle_state, created_by, updated_by
) VALUES (
    'OA_PROD_LEAVE', 'default', 'leave-api', 'Leave API', 'service-openapi',
    'Internal local integration clients', 'MEDIUM', 'TENANT',
    'Seed product exposing the leave.submit OpenAPI route.', 'Internal development use only.',
    '["leave.submit"]'::jsonb, '{"maxConcurrency":100,"maxActiveWorkflows":20}'::jsonb,
    '{"gatewayRequired":true}'::jsonb, 'ACTIVE', 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_api_product_version (
    id, api_product_id, semantic_version, major_version, minor_version, patch_version,
    lifecycle_state, documentation, terms, pinned_routes, pinned_contracts, scopes,
    traffic_policy, security_policy, change_classification, validation_result,
    published_by, published_at, created_by, updated_by
) VALUES (
    'OA_PRODV_LEAVE_1', 'OA_PROD_LEAVE', '1.0.0', 1, 0, 0,
    'PUBLISHED', 'Leave submit API version 1.0.0.', 'Internal development use only.',
    '[{"routeKey":"leave.submit","releaseSnapshotId":"OA_REL_LEAVE_SUBMIT_1"}]'::jsonb,
    '["OA_SV_LEAVE_PUBLIC_1","OA_SV_LEAVE_ACTION_1"]'::jsonb,
    '["leave.submit"]'::jsonb,
    '{"maxConcurrency":100,"maxActiveWorkflows":20}'::jsonb,
    '{"gatewayRequired":true}'::jsonb, 'MINOR', '{"valid":true,"seeded":true}'::jsonb,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_api_product_route_member (
    id, api_product_version_id, route_key, release_snapshot_id, operations, scopes,
    canonical_structure_version_ids, created_by, updated_by
) VALUES (
    'OA_PRODR_LEAVE_1', 'OA_PRODV_LEAVE_1', 'leave.submit', 'OA_REL_LEAVE_SUBMIT_1',
    '["POST"]'::jsonb, '["leave.submit"]'::jsonb,
    '["OA_SV_LEAVE_PUBLIC_1","OA_SV_LEAVE_ACTION_1"]'::jsonb,
    'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_application (
    id, tenant_id, application_key, display_name, owner_id, purpose,
    risk_level, lifecycle_state, approval_evidence, created_by, updated_by
) VALUES (
    'OA_APP_LEAVE_CLIENT', 'default', 'leave-demo-client', 'Leave demo client',
    'service-openapi', 'Local end-to-end acceptance for the leave.submit route.',
    'MEDIUM', 'ACTIVE', '{"seeded":true}'::jsonb, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_application_client (
    id, application_id, tenant_id, environment, client_key, lifecycle_state,
    network_policy, security_policy, created_by, updated_by
) VALUES (
    'OA_CLIENT_LEAVE_DEV', 'OA_APP_LEAVE_CLIENT', 'default', 'DEV',
    'leave-demo-dev', 'ACTIVE', '{}'::jsonb, '{"gatewayRequired":true}'::jsonb,
    'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;

INSERT INTO oa_product_subscription (
    id, tenant_id, application_client_id, api_product_version_id, environment,
    lifecycle_state, requested_scopes, effective_scopes, overrides, effective_from,
    requested_by, activated_at, created_by, updated_by
) VALUES (
    'OA_SUB_LEAVE_DEV', 'default', 'OA_CLIENT_LEAVE_DEV', 'OA_PRODV_LEAVE_1', 'DEV',
    'ACTIVE', '["leave.submit"]'::jsonb, '["leave.submit"]'::jsonb,
    '{}'::jsonb, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
) ON CONFLICT DO NOTHING;
