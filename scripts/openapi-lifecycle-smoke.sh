#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENAPI_BASE_URL:-http://localhost:8088}"
INTERNAL_BASE_URL="${OPENAPI_INTERNAL_BASE_URL:-$BASE_URL}"
TENANT_ID="${OPENAPI_SMOKE_TENANT:-tenant-smoke}"
RUN_ID="${OPENAPI_SMOKE_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
OWNER_USER="${OPENAPI_SMOKE_OWNER:-openapi-smoke-owner}"
APPROVER_USER="${OPENAPI_SMOKE_APPROVER:-openapi-smoke-approver}"

PERMISSIONS=$(
  IFS=,
  echo "/api/v1/openapi/management/structures:GET,/api/v1/openapi/management/structures:POST,/api/v1/openapi/management/structures/*:PUT,/api/v1/openapi/management/structures/*/publish:POST,/api/v1/openapi/management/mappings:GET,/api/v1/openapi/management/mappings:POST,/api/v1/openapi/management/connectors:GET,/api/v1/openapi/management/connectors:POST,/api/v1/openapi/management/routes:GET,/api/v1/openapi/management/routes:POST,/api/v1/openapi/management/products:GET,/api/v1/openapi/management/products:POST,/api/v1/openapi/management/applications:GET,/api/v1/openapi/management/applications:POST,/api/v1/openapi/management/approvals:POST,/api/v1/openapi/management/operations:GET,/api/v1/openapi/management/executions:GET"
)

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required tool: $1" >&2
    exit 1
  fi
}

request_api() {
  local base_url="$1"
  local user="$2"
  local method="$3"
  local path="$4"
  local body="${5:-}"
  local response status payload code message

  if [[ -n "$body" ]]; then
    response=$(curl -sS -w '\n%{http_code}' -X "$method" "${base_url}${path}" \
      -H "X-User-Id: ${user}" \
      -H "X-Username: ${user}" \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -H "X-User-Permissions: ${PERMISSIONS}" \
      -H 'Content-Type: application/json' \
      --data-binary "$body")
  else
    response=$(curl -sS -w '\n%{http_code}' -X "$method" "${base_url}${path}" \
      -H "X-User-Id: ${user}" \
      -H "X-Username: ${user}" \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -H "X-User-Permissions: ${PERMISSIONS}")
  fi

  status=$(printf '%s\n' "$response" | tail -n 1)
  payload=$(printf '%s\n' "$response" | sed '$d')
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "HTTP ${status} ${method} ${path}" >&2
    echo "$payload" >&2
    exit 1
  fi
  code=$(jq -r '.code // 0' <<<"$payload")
  message=$(jq -r '.message // ""' <<<"$payload")
  if [[ "$code" != "0" ]]; then
    echo "API code ${code} ${method} ${path}: ${message}" >&2
    echo "$payload" | jq . >&2
    exit 1
  fi
  printf '%s\n' "$payload"
}

api() {
  request_api "$BASE_URL" "$@"
}

internal_api() {
  request_api "$INTERNAL_BASE_URL" "$@"
}

runtime_api() {
  local method="$1"
  local path="$2"
  local client_key="$3"
  local credential="$4"
  local body="${5:-}"
  local response status payload code message

  response=$(curl -sS -w '\n%{http_code}' -X "$method" "${BASE_URL}${path}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -H 'X-Environment: TEST' \
    -H "X-Client-Key: ${client_key}" \
    -H "X-Client-Credential: ${credential}" \
    -H 'X-Scopes: orders.read' \
    -H 'Content-Type: application/json' \
    --data-binary "$body")
  status=$(printf '%s\n' "$response" | tail -n 1)
  payload=$(printf '%s\n' "$response" | sed '$d')
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "HTTP ${status} ${method} ${path}" >&2
    echo "$payload" >&2
    exit 1
  fi
  code=$(jq -r '.code // 0' <<<"$payload")
  message=$(jq -r '.message // ""' <<<"$payload")
  if [[ "$code" != "0" ]]; then
    echo "API code ${code} ${method} ${path}: ${message}" >&2
    echo "$payload" | jq . >&2
    exit 1
  fi
  printf '%s\n' "$payload"
}

trusted_runtime_api() {
  local method="$1"
  local path="$2"
  local client_id="$3"
  local subscription_id="$4"
  local policy_version="$5"
  local max_concurrency="$6"
  local body="${7:-}"
  local response status payload code message

  response=$(curl -sS -w '\n%{http_code}' -X "$method" "${BASE_URL}${path}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -H 'X-Environment: TEST' \
    -H 'X-Gateway-Authenticated: true' \
    -H "X-OpenAPI-Gateway-Secret: ${OPENAPI_GATEWAY_AUTH_SECRET}" \
    -H "X-Application-Client-Id: ${client_id}" \
    -H "X-Subscription-Id: ${subscription_id}" \
    -H "X-Policy-Version: ${policy_version}" \
    -H "X-Max-Concurrency: ${max_concurrency}" \
    -H 'Content-Type: application/json' \
    --data-binary "$body")
  status=$(printf '%s\n' "$response" | tail -n 1)
  payload=$(printf '%s\n' "$response" | sed '$d')
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "HTTP ${status} ${method} ${path}" >&2
    echo "$payload" >&2
    exit 1
  fi
  code=$(jq -r '.code // 0' <<<"$payload")
  message=$(jq -r '.message // ""' <<<"$payload")
  if [[ "$code" != "0" ]]; then
    echo "API code ${code} ${method} ${path}: ${message}" >&2
    echo "$payload" | jq . >&2
    exit 1
  fi
  printf '%s\n' "$payload"
}

data() {
  jq -r '.data'"${1:-}" <<<"$2"
}

approve_pending_for_asset() {
  local asset_id="$1"
  local approvals approval_id
  approvals=$(api "$APPROVER_USER" GET "/api/v1/openapi/management/operations/assets/approvals?keyword=${asset_id}&page=1&size=20")
  while read -r approval_id; do
    [[ -z "$approval_id" || "$approval_id" == "null" ]] && continue
    api "$APPROVER_USER" POST "/api/v1/openapi/management/approvals/${approval_id}/decision" \
      '{"approved":true,"evidence":{"source":"openapi-lifecycle-smoke"}}' >/dev/null
  done < <(jq -r --arg asset_id "$asset_id" '.data.records[] | select(.detail.assetId == $asset_id and .detail.decision == "PENDING") | .id' <<<"$approvals")
}

require_tool curl
require_tool jq
require_tool python3

MOCK_PORT="${OPENAPI_SMOKE_MOCK_PORT:-$(python3 - <<'PY'
import socket
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
)}"
python3 - "$MOCK_PORT" <<'PY' &
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

port = int(sys.argv[1])

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")
            return
        self._respond()

    def do_POST(self):
        self._respond()

    def log_message(self, *_):
        pass

    def _respond(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        payload = json.loads(self.rfile.read(length) or b"{}")
        response = {
            "orderId": payload.get("orderId", "mock-order"),
            "amount": payload.get("amount", 0),
            "status": "FULFILLED"
        }
        body = json.dumps(response).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

HTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
MOCK_PID=$!
trap 'kill "$MOCK_PID" >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${MOCK_PORT}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done
if ! curl -fsS "http://127.0.0.1:${MOCK_PORT}/health" >/dev/null 2>&1; then
  echo "partner mock failed to start on port ${MOCK_PORT}" >&2
  exit 1
fi

health=$(curl -sS "${INTERNAL_BASE_URL}/health")
if [[ "$(jq -r '.status // empty' <<<"$health")" != "UP" ]]; then
  echo "service-openapi health check failed: ${health}" >&2
  exit 1
fi

schema='{"type":"object","required":["orderId"],"properties":{"orderId":{"type":"string","x-triobase-semantic-id":"order.id"},"amount":{"type":"number"},"status":{"type":"string"}}}'
canonical_key="smoke.order.canonical.${RUN_ID}"
external_key="smoke.order.external.${RUN_ID}"
mapping_key="smoke.order.mapping.${RUN_ID}"
response_mapping_key="smoke.order.response.mapping.${RUN_ID}"
value_map_key="smoke.order.status.${RUN_ID}"
connector_key="smoke.order.connector.${RUN_ID}"
route_key="smoke.orders.get.${RUN_ID}"
product_key="smoke-order-product-${RUN_ID}"
application_key="smoke-app-${RUN_ID}"
client_key="smoke-client-${RUN_ID}"

canonical=$(api "$OWNER_USER" POST /api/v1/openapi/management/structures "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$canonical_key" --argjson schema "$schema" \
  '{tenantId:$tenant,namespace:"smoke",structureKey:$key,displayName:"Smoke canonical order",structureKind:"CANONICAL",direction:"BIDIRECTIONAL",ownerType:"DOMAIN",ownerId:"smoke",schemaContent:$schema,changeSummary:"initial smoke canonical"}')")
canonical_structure_id=$(data '.id' "$canonical")
canonical_version_id=$(data '.latestVersionId' "$canonical")
api "$OWNER_USER" POST "/api/v1/openapi/management/structures/versions/${canonical_version_id}/publish" >/dev/null

external=$(api "$OWNER_USER" POST /api/v1/openapi/management/structures "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$external_key" --argjson schema "$schema" \
  '{tenantId:$tenant,namespace:"smoke",structureKey:$key,displayName:"Smoke external order",structureKind:"EXTERNAL",direction:"BIDIRECTIONAL",ownerType:"PARTNER",ownerId:"smoke",schemaContent:$schema,changeSummary:"initial smoke external"}')")
external_structure_id=$(data '.id' "$external")
external_version_id=$(data '.latestVersionId' "$external")
api "$OWNER_USER" POST "/api/v1/openapi/management/structures/versions/${external_version_id}/publish" >/dev/null

rules='[{"order":1,"operation":"COPY","sourcePointer":"/orderId","targetPointer":"/orderId","config":{},"required":true},{"order":2,"operation":"COPY","sourcePointer":"/amount","targetPointer":"/amount","config":{},"required":false},{"order":3,"operation":"COPY","sourcePointer":"/status","targetPointer":"/status","config":{},"required":false}]'
mapping=$(api "$OWNER_USER" POST /api/v1/openapi/management/mappings "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$mapping_key" --arg canonical "$canonical_structure_id" \
  --arg external "$external_structure_id" --arg source "$canonical_version_id" --arg target "$external_version_id" \
  --argjson rules "$rules" \
  '{tenantId:$tenant,mappingKey:$key,displayName:"Smoke order mapping",direction:"CANONICAL_TO_EXTERNAL",canonicalStructureId:$canonical,externalStructureId:$external,sourceStructureVersionId:$source,targetStructureVersionId:$target,ownerId:"smoke",rules:$rules}')")
mapping_version_id=$(data '.mappingVersionId' "$mapping")
api "$OWNER_USER" POST "/api/v1/openapi/management/mappings/versions/${mapping_version_id}/publish" >/dev/null

response_mapping=$(api "$OWNER_USER" POST /api/v1/openapi/management/mappings "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$response_mapping_key" --arg canonical "$canonical_structure_id" \
  --arg external "$external_structure_id" --arg source "$external_version_id" --arg target "$canonical_version_id" \
  --argjson rules "$rules" \
  '{tenantId:$tenant,mappingKey:$key,displayName:"Smoke order response mapping",direction:"EXTERNAL_TO_CANONICAL",canonicalStructureId:$canonical,externalStructureId:$external,sourceStructureVersionId:$source,targetStructureVersionId:$target,ownerId:"smoke",rules:$rules}')")
response_mapping_version_id=$(data '.mappingVersionId' "$response_mapping")
api "$OWNER_USER" POST "/api/v1/openapi/management/mappings/versions/${response_mapping_version_id}/publish" >/dev/null

value_map=$(api "$OWNER_USER" POST /api/v1/openapi/management/value-maps "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$value_map_key" \
  '{tenantId:$tenant,valueMapKey:$key,displayName:"Smoke status map",ownerId:"smoke",caseSensitive:true,unmappedPolicy:"PASS_THROUGH",entries:[{canonicalValue:"NEW",externalValue:"N",order:1}]}')")
value_map_version_id=$(data '.id' "$value_map")
api "$OWNER_USER" POST "/api/v1/openapi/management/value-maps/versions/${value_map_version_id}/publish" >/dev/null

connector=$(api "$OWNER_USER" POST /api/v1/openapi/management/connectors "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$connector_key" --arg base "http://127.0.0.1:${MOCK_PORT}" \
  '{tenantId:$tenant,connectorKey:$key,displayName:"Smoke connector",ownerId:"smoke",baseUrl:$base,operationPath:"/orders",httpMethod:"POST",timeoutMillis:400,operationClass:"READ_ONLY",authenticationType:"NONE",networkPolicy:{allowedHosts:["127.0.0.1"],allowPrivateNetwork:true},responseSizeLimit:1048576}')")
connector_version_id=$(data '.connectorVersionId' "$connector")
api "$OWNER_USER" POST "/api/v1/openapi/management/connectors/versions/${connector_version_id}/publish" >/dev/null

route=$(api "$OWNER_USER" POST /api/v1/openapi/management/routes "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$route_key" --arg connector "$connector_version_id" \
  --arg requestMapping "$mapping_version_id" --arg responseMapping "$response_mapping_version_id" \
  '{tenantId:$tenant,routeKey:$key,displayName:"Smoke orders post",ownerId:"smoke",environment:"TEST",priority:10,enabled:true,routePredicate:{},executionMode:"SYNCHRONOUS",connectorVersionId:$connector,requestMappingVersionId:$requestMapping,responseMappingVersionId:$responseMapping}')")
route_id=$(data '.routeId' "$route")
route_version_id=$(data '.routeVersionId' "$route")
api "$OWNER_USER" POST "/api/v1/openapi/management/routes/versions/${route_version_id}/publish" >/dev/null
release=$(api "$OWNER_USER" POST "/api/v1/openapi/management/routes/versions/${route_version_id}/releases" '{"releaseNotes":"smoke release"}')
release_id=$(data '.releaseId' "$release")
api "$OWNER_USER" POST "/api/v1/openapi/management/releases/${release_id}/activate" >/dev/null

product=$(api "$OWNER_USER" POST /api/v1/openapi/management/products "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$product_key" --arg route "$route_key" --arg release "$release_id" --arg canonical "$canonical_version_id" \
  '{tenantId:$tenant,productKey:$key,displayName:"Smoke order product",ownerId:"smoke",audience:"internal smoke",riskLevel:"LOW",visibility:"TENANT",documentation:"smoke",terms:"smoke",defaultScopes:["orders.read"],defaultTrafficPolicy:{ratePerSecond:100,maxBodyBytes:1048576,maxConcurrency:5},defaultSecurityPolicy:{},semanticVersion:"1.0.0",changeClassification:"MINOR",routes:[{routeKey:$route,releaseSnapshotId:$release,operations:["POST"],scopes:["orders.read"],canonicalStructureVersionIds:[$canonical]}]}')")
product_version_id=$(data '.productVersionId' "$product")
api "$OWNER_USER" POST "/api/v1/openapi/management/products/versions/${product_version_id}/publish" >/dev/null

application=$(api "$OWNER_USER" POST /api/v1/openapi/management/applications "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$application_key" \
  '{tenantId:$tenant,applicationKey:$key,displayName:"Smoke application",ownerId:"smoke",purpose:"OpenAPI lifecycle smoke",riskLevel:"LOW",contacts:[{role:"OWNER",name:"Smoke Owner",email:"smoke@example.com"}]}')")
application_id=$(data '.applicationId' "$application")
api "$OWNER_USER" POST "/api/v1/openapi/management/applications/${application_id}/submit" >/dev/null
approve_pending_for_asset "$application_id"
api "$OWNER_USER" POST "/api/v1/openapi/management/applications/${application_id}/activate" >/dev/null

client=$(api "$OWNER_USER" POST "/api/v1/openapi/management/applications/${application_id}/clients" "$(jq -nc \
  --arg key "$client_key" '{environment:"TEST",clientKey:$key,networkPolicy:{},securityPolicy:{}}')")
client_id=$(data '.clientId' "$client")
credential=$(api "$OWNER_USER" POST "/api/v1/openapi/management/applications/clients/${client_id}/credentials/rotate" "$(jq -nc \
  --arg ref "smoke/${RUN_ID}/client-key" '{authenticationType:"API_KEY",newSecretReference:$ref,overlapSeconds:0}')")
credential_id=$(data '.bindingId' "$credential")
api_key=$(data '.oneTimeSecret.apiKey' "$credential")

subscription=$(api "$OWNER_USER" POST /api/v1/openapi/management/subscriptions "$(jq -nc \
  --arg client "$client_id" --arg product "$product_version_id" \
  '{applicationClientId:$client,apiProductVersionId:$product,requestedScopes:["orders.read"],routeOverrides:[]}')")
subscription_id=$(data '.subscriptionId' "$subscription")
approve_pending_for_asset "$subscription_id"
api "$OWNER_USER" POST "/api/v1/openapi/management/subscriptions/${subscription_id}/activate" >/dev/null

policy=$(api "$OWNER_USER" POST /api/v1/openapi/management/policies "$(jq -nc \
  --arg tenant "$TENANT_ID" '{tenantId:$tenant,environment:"TEST",scopeType:"TENANT",scopeId:$tenant,policyContent:{ratePerSecond:100,maxBodyBytes:1048576,requireTls:false}}')")
policy_id=$(data '.id' "$policy")
api "$APPROVER_USER" POST "/api/v1/openapi/management/policies/${policy_id}/publish" >/dev/null
snapshot=$(api "$APPROVER_USER" POST "/api/v1/openapi/management/policies/snapshots?tenantId=${TENANT_ID}&environment=TEST")
snapshot_version=$(data '.snapshotVersion' "$snapshot")
api "$APPROVER_USER" POST "/api/v1/openapi/management/policies/enforcement/platform-gateway/applied?tenantId=${TENANT_ID}&environment=TEST&version=${snapshot_version}" >/dev/null
api "$APPROVER_USER" POST "/api/v1/openapi/management/policies/enforcement/service-openapi-runtime/applied?tenantId=${TENANT_ID}&environment=TEST&version=${snapshot_version}" >/dev/null

runtime_body=$(jq -nc --arg id "SO-${RUN_ID}" '{orderId:$id,amount:42,status:"NEW"}')
runtime_body_bytes=$(printf '%s' "$runtime_body" | wc -c | tr -d ' ')
admission=$(internal_api "$OWNER_USER" POST /api/v1/openapi/internal/admission "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "$client_key" --arg route "$route_key" --arg credential "$api_key" \
  --argjson bytes "$runtime_body_bytes" \
  '{tenantId:$tenant,clientKey:$key,environment:"TEST",routeKey:$route,operation:"POST",credential:$credential,sourceIp:"127.0.0.1",contentLength:$bytes,tls:false,scopes:["orders.read"],callback:false}')")
admission_allowed=$(data '.allowed' "$admission")
if [[ "$admission_allowed" != "true" ]]; then
  echo "$admission" | jq . >&2
  exit 1
fi
policy_version=$(data '.policyVersion' "$admission")
max_concurrency=$(data '.maxConcurrency' "$admission")
runtime_admission="direct-admission"
if [[ "$BASE_URL" != "$INTERNAL_BASE_URL" ]]; then
  runtime_admission="gateway-admission"
fi
if [[ "${OPENAPI_TRUSTED_GATEWAY_CONTEXT:-false}" == "true" ]]; then
  if [[ -z "${OPENAPI_GATEWAY_AUTH_SECRET:-}" ]]; then
    echo "OPENAPI_GATEWAY_AUTH_SECRET is required when OPENAPI_TRUSTED_GATEWAY_CONTEXT=true" >&2
    exit 1
  fi
  runtime=$(trusted_runtime_api POST "/api/v1/openapi/runtime/${route_key}" \
    "$client_id" "$subscription_id" "$policy_version" "$max_concurrency" "$runtime_body")
  runtime_admission="trusted-gateway-context"
else
  runtime=$(runtime_api POST "/api/v1/openapi/runtime/${route_key}" "$client_key" "$api_key" "$runtime_body")
fi
execution_id=$(data '.executionId' "$runtime")
partner_status=$(data '.partnerStatus' "$runtime")
runtime_status=$(data '.body.status' "$runtime")
if [[ "$partner_status" != "200" || "$runtime_status" != "FULFILLED" ]]; then
  echo "$runtime" | jq . >&2
  exit 1
fi
execution=$(api "$OWNER_USER" GET "/api/v1/openapi/management/executions/${execution_id}")
execution_state=$(data '.execution.executionState' "$execution")
attempt_state=$(data '.attempts[0].attemptState' "$execution")
if [[ "$execution_state" != "SUCCEEDED" || "$attempt_state" != "SUCCEEDED" ]]; then
  echo "$execution" | jq . >&2
  exit 1
fi

readiness=$(api "$OWNER_USER" GET /api/v1/openapi/management/operations/readiness)
ready=$(data '.ready' "$readiness")
public_runtime=$(data '.publicRuntimeEnabled' "$readiness")

cat <<REPORT
OpenAPI lifecycle smoke complete
tenant=${TENANT_ID}
runId=${RUN_ID}
canonicalVersion=${canonical_version_id}
externalVersion=${external_version_id}
mappingVersion=${mapping_version_id}
responseMappingVersion=${response_mapping_version_id}
valueMapVersion=${value_map_version_id}
connectorVersion=${connector_version_id}
route=${route_key}
routeId=${route_id}
release=${release_id}
productVersion=${product_version_id}
application=${application_id}
client=${client_id}
credential=${credential_id}
subscription=${subscription_id}
policy=${policy_id}
snapshotVersion=${snapshot_version}
admissionAllowed=${admission_allowed}
runtimeAdmission=${runtime_admission}
execution=${execution_id}
partnerStatus=${partner_status}
executionState=${execution_state}
readiness.ready=${ready}
readiness.publicRuntimeEnabled=${public_runtime}
REPORT

if [[ "$ready" != "true" ]]; then
  echo "$readiness" | jq . >&2
  exit 1
fi
