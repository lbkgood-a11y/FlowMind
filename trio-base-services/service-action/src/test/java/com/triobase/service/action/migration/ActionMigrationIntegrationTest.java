package com.triobase.service.action.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ActionMigrationIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(System.getProperty("test.postgres.image", "postgres:16-alpine"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("triobase_action_test")
                    .withUsername("triobase")
                    .withPassword("triobase");

    @Test
    void appliesActionRuntimeMigrationsToCleanPostgres() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_schema_history_action_test")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertColumnCount(statement, "act_action_execution",
                    "tenant_id", "action_type", "source", "actor_type", "actor_id",
                    "target_type", "target_id", "status", "execution_mode", "audit_level",
                    "idempotency_key", "correlation_id", "request_id", "trace_id",
                    "owner_service", "owner_execution_ref", "payload_summary",
                    "result_summary", "error_summary", "retryable", "completed_at");
            assertColumnCount(statement, "act_action_event",
                    "action_id", "tenant_id", "event_type", "status", "sequence_no",
                    "message", "event_data_json", "trace_id", "occurred_at");
            assertColumnCount(statement, "act_action_definition_snapshot",
                    "action_type", "owner_service", "target_type", "version", "status",
                    "definition_json", "schema_hash", "published_at");
            assertColumnCount(statement, "act_action_dispatch",
                    "action_id", "tenant_id", "owner_service", "owner_endpoint",
                    "dispatch_status", "attempt_count", "max_attempts", "next_retry_at",
                    "last_error", "locked_by", "locked_at", "dispatched_at", "completed_at");

            assertIndexExists(statement, "act_action_execution", "uk_act_action_execution_idempotency");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_tenant");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_type");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_actor");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_source");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_target");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_status");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_trace");
            assertIndexExists(statement, "act_action_execution", "idx_act_action_execution_correlation");
            assertIndexExists(statement, "act_action_event", "uk_act_action_event_action_sequence");
            assertIndexExists(statement, "act_action_definition_snapshot",
                    "uk_act_action_definition_snapshot_version");
            assertIndexExists(statement, "act_action_dispatch", "uk_act_action_dispatch_action");

            insertExecution(statement, "A1", "T1", "process.task.approve", "K1");
            assertThatThrownBy(() -> insertExecution(statement, "A2", "T1",
                    "process.task.approve", "K1"))
                    .isInstanceOf(Exception.class);
            insertExecution(statement, "A3", "T2", "process.task.approve", "K1");
            insertExecution(statement, "A4", "T1", "process.task.approve", null);
            insertExecution(statement, "A5", "T1", "process.task.approve", null);

            insertEvent(statement, "E1", "A1", 1);
            assertThatThrownBy(() -> insertEvent(statement, "E2", "A1", 1))
                    .isInstanceOf(Exception.class);

            statement.executeUpdate("""
                    INSERT INTO act_action_definition_snapshot(
                        id, action_type, owner_service, version, status, definition_json)
                    VALUES ('D1', 'process.task.approve', 'service-workflow-engine', 1, 'ACTIVE', '{}')
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO act_action_definition_snapshot(
                        id, action_type, owner_service, version, status, definition_json)
                    VALUES ('D2', 'process.task.approve', 'service-workflow-engine', 1, 'ACTIVE', '{}')
                    """)).isInstanceOf(Exception.class);

            statement.executeUpdate("""
                    INSERT INTO act_action_dispatch(
                        id, action_id, tenant_id, owner_service, dispatch_status)
                    VALUES ('DSP1', 'A1', 'T1', 'service-workflow-engine', 'PENDING')
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO act_action_dispatch(
                        id, action_id, tenant_id, owner_service, dispatch_status)
                    VALUES ('DSP2', 'A1', 'T1', 'service-workflow-engine', 'PENDING')
                    """)).isInstanceOf(Exception.class);
        }

        MigrateResult repeated = flyway.migrate();
        assertThat(repeated.migrationsExecuted).isZero();
    }

    private void insertExecution(Statement statement,
                                 String id,
                                 String tenantId,
                                 String actionType,
                                 String idempotencyKey) throws Exception {
        String idem = idempotencyKey == null ? "NULL" : "'" + idempotencyKey + "'";
        statement.executeUpdate("""
                INSERT INTO act_action_execution(
                    id, tenant_id, action_type, source, status, idempotency_key)
                VALUES ('%s', '%s', '%s', 'GUI', 'CREATED', %s)
                """.formatted(id, tenantId, actionType, idem));
    }

    private void insertEvent(Statement statement, String id, String actionId, int sequenceNo) throws Exception {
        statement.executeUpdate("""
                INSERT INTO act_action_event(
                    id, action_id, tenant_id, event_type, status, sequence_no)
                VALUES ('%s', '%s', 'T1', 'CREATED', 'CREATED', %d)
                """.formatted(id, actionId, sequenceNo));
    }

    private void assertColumnCount(Statement statement, String tableName, String... columnNames) throws Exception {
        String inClause = "'" + String.join("','", columnNames) + "'";
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '%s'
                  AND column_name IN (%s)
                """.formatted(tableName, inClause))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(columnNames.length);
        }
    }

    private void assertIndexExists(Statement statement, String tableName, String indexName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = '%s'
                  AND indexname = '%s'
                """.formatted(tableName, indexName))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
