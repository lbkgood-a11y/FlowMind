# Process Runtime Local Development

## Infrastructure

Start PostgreSQL, Temporal, Redis, and the remaining shared infrastructure:

```powershell
docker compose -f docker/docker-compose.yml up -d postgres temporal temporal-web redis
```

Local defaults:

| Dependency | Address |
|---|---|
| PostgreSQL | `localhost:5433/triobase` |
| Temporal | `127.0.0.1:7233` |
| Temporal Web | `http://localhost:8088` |
| Auth | `http://localhost:8081` |
| Org | `http://localhost:8082` |
| Lowcode | `http://localhost:8085` |
| Workflow Engine | `http://localhost:8086` |
| Platform Gateway | `http://localhost:8080` |

## Required Environment

Use `docker/.env.example` as the local variable reference. The same
`INTERNAL_SERVICE_TOKEN` value must be configured in auth, org, lowcode, and
workflow-engine. Internal `/internal/v1/**` endpoints are called directly by
workflow-engine and are intentionally absent from gateway routes.

The embedded Temporal Worker uses `spring.application.name` as its task queue.
For workflow-engine this must remain `service-workflow-engine`; changing only
the Worker queue or only the client queue will leave executions unpolled.

## Startup Order

1. Start Docker infrastructure.
2. Start `service-auth`, `service-org`, and `service-lowcode` with one shared internal token.
3. Start `service-workflow-engine` and confirm Flyway reaches workflow schema v34.
4. Start `platform-gateway` and the Vben frontend.

Workflow schema v30-v34 add the business object catalog, expense-report
fixtures, process package closure snapshots, business launch fields, and
standardized process business events. The seed catalog is documented in
[`business-process-closure-foundation.md`](business-process-closure-foundation.md).

Every external model call remains outside this workflow path and must still pass
through the API gateway and LLM gateway double-sanitization controls.
