#!/usr/bin/env bash
set -euo pipefail

pnpm --dir trio-base-frontend -F @vben/web-antd run typecheck
pnpm --package=@redocly/cli@1.34.5 dlx redocly lint docs/api/service-openapi.yaml
mvn -pl trio-base-services/service-openapi -am \
  -Dtest=OpenApiMigrationIntegrationTest,OpenApiArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
