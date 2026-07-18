#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${OPENAPI_DEMO_DB_HOST:-localhost}"
DB_PORT="${OPENAPI_DEMO_DB_PORT:-5433}"
DB_NAME="${OPENAPI_DEMO_DB_NAME:-triobase}"
DB_USER="${OPENAPI_DEMO_DB_USER:-triobase}"
DB_PASSWORD="${OPENAPI_DEMO_DB_PASSWORD:-triobase123}"
TENANT_ID="${OPENAPI_DEMO_TENANT:-tenant-impl-demo}"
RUN_ID="${OPENAPI_DEMO_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
BASE_URL="${OPENAPI_BASE_URL:-http://localhost:8088}"
OWNER_USER="${OPENAPI_DEMO_OWNER:-openapi-impl-owner}"
DEMO_PERMISSIONS="/api/v1/openapi/management/structures:POST,/api/v1/openapi/management/applications:POST,/api/v1/openapi/management/products:POST"

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required tool: $1" >&2
    exit 1
  fi
}

require_tool curl
require_tool docker
require_tool jq
require_tool python3

run_psql() {
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${DB_PASSWORD}" psql \
      -h "${DB_HOST}" \
      -p "${DB_PORT}" \
      -U "${DB_USER}" \
      -d "${DB_NAME}" \
      -v ON_ERROR_STOP=1
    return
  fi
  if docker ps --format '{{.Names}}' | grep -qx 'triobase-postgres'; then
    docker exec -i triobase-postgres psql \
      -U "${DB_USER}" \
      -d "${DB_NAME}" \
      -v ON_ERROR_STOP=1
    return
  fi
  echo "Missing psql and running triobase-postgres container" >&2
  exit 1
}

request_demo_api() {
  local method="$1"
  local path="$2"
  local body="$3"
  local response status payload code message

  response=$(curl -sS -w '\n%{http_code}' -X "${method}" "${BASE_URL}${path}" \
    -H "X-User-Id: ${OWNER_USER}" \
    -H "X-Username: ${OWNER_USER}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -H "X-User-Permissions: ${DEMO_PERMISSIONS}" \
    -H 'Content-Type: application/json' \
    --data-binary "${body}")
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

echo "Clearing OpenAPI lifecycle tables on ${DB_HOST}:${DB_PORT}/${DB_NAME}"
run_psql <<'SQL'
DO $$
DECLARE
    tables text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
      INTO tables
      FROM pg_tables
     WHERE schemaname = 'public'
       AND tablename LIKE 'oa\_%' ESCAPE '\';

    IF tables IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || tables || ' RESTART IDENTITY CASCADE';
    END IF;
END $$;
SQL

echo "Creating implementer demo OpenAPI lifecycle data for tenant=${TENANT_ID}, runId=${RUN_ID}"
OPENAPI_SMOKE_TENANT="${TENANT_ID}" \
OPENAPI_SMOKE_RUN_ID="${RUN_ID}" \
OPENAPI_SMOKE_OWNER="${OWNER_USER}" \
OPENAPI_SMOKE_APPROVER="${OPENAPI_DEMO_APPROVER:-openapi-impl-approver}" \
"${ROOT_DIR}/scripts/openapi-lifecycle-smoke.sh"

echo "Creating editable draft samples for visual edit demos"
draft_schema='{"type":"object","required":["draftId"],"properties":{"draftId":{"type":"string"},"remark":{"type":"string"}}}'
draft_structure=$(request_demo_api POST /api/v1/openapi/management/structures "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "demo.editable.structure.${RUN_ID}" --argjson schema "$draft_schema" \
  '{tenantId:$tenant,namespace:"demo",structureKey:$key,displayName:"Editable draft structure",structureKind:"CANONICAL",direction:"BIDIRECTIONAL",ownerType:"DOMAIN",ownerId:"implementation",schemaContent:$schema,changeSummary:"draft for visual edit demo"}')")
draft_application=$(request_demo_api POST /api/v1/openapi/management/applications "$(jq -nc \
  --arg tenant "$TENANT_ID" --arg key "demo-editable-app-${RUN_ID}" \
  '{tenantId:$tenant,applicationKey:$key,displayName:"Editable draft application",ownerId:"implementation",purpose:"Visual edit demo",riskLevel:"LOW",contacts:[{role:"OWNER",name:"Implementation Owner",email:"implementation@example.com"}]}')")
draft_policy=$(request_demo_api POST /api/v1/openapi/management/policies "$(jq -nc \
  --arg tenant "$TENANT_ID" \
  '{tenantId:$tenant,environment:"TEST",scopeType:"TENANT",scopeId:$tenant,policyContent:{ratePerSecond:10,maxBodyBytes:262144,requireTls:false}}')")

cat <<REPORT
Editable draft samples
draftStructureVersion=$(jq -r '.data.latestVersionId' <<<"$draft_structure")
draftApplication=$(jq -r '.data.applicationId' <<<"$draft_application")
draftPolicy=$(jq -r '.data.id' <<<"$draft_policy")
REPORT
