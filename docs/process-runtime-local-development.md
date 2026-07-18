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

`service-lowcode` must run its own Flyway migrations before workflow packages
can publish lowcode-backed form snapshots. Local defaults enable
`LOWCODE_FLYWAY_ENABLED=true`, use `LOWCODE_DB_URL`, and write migration history
to `flyway_schema_history_lowcode`. Keep this table separate from the workflow
Flyway history table because lowcode owns all `lc_*` tables and workflow-engine
owns `wf_*` tables.

The embedded Temporal Worker uses `spring.application.name` as its task queue.
For workflow-engine this must remain `service-workflow-engine`; changing only
the Worker queue or only the client queue will leave executions unpolled.

## Startup Order

1. Start Docker infrastructure.
2. Start `service-auth` and `service-org` with the shared internal token.
3. Start `service-lowcode`, confirm Flyway reaches lowcode schema v7, and check
   `/internal/v1/process-forms/{id}` can return only published form snapshots.
4. Start `service-workflow-engine` and confirm Flyway reaches workflow schema v34.
5. Start `platform-gateway` and the Vben frontend.

Workflow schema v30-v34 add the business object catalog, expense-report
fixtures, process package closure snapshots, business launch fields, and
standardized process business events. The seed catalog is documented in
[`business-process-closure-foundation.md`](business-process-closure-foundation.md).
Lowcode schema v4-v7 add tenant/versioned form metadata, form instance workflow
audit fields, rapid-application metadata, and the generic expense application
seed used by the migrated runtime.

Every external model call remains outside this workflow path and must still pass
through the API gateway and LLM gateway double-sanitization controls.
