package com.triobase.service.lowcode.migration;

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
class LowcodeMigrationIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(System.getProperty("test.postgres.image", "postgres:16-alpine"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("triobase_lowcode_test")
                    .withUsername("triobase")
                    .withPassword("triobase");

    @Test
    void appliesLowcodeOwnedMigrationsToCleanPostgres() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_schema_history_lowcode_test")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(6);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertColumnCount(statement, "lc_form_definition",
                    "tenant_id", "schema_hash", "published_at", "offline_at", "source_form_definition_id");
            assertColumnCount(statement, "lc_form_instance",
                    "tenant_id", "form_definition_version", "schema_hash", "process_key",
                    "process_instance_id", "workflow_status", "workflow_bound_at",
                    "workflow_status_updated_at", "process_binding_trace_id");
            assertIndexExists(statement, "lc_form_definition", "uk_lc_form_definition_tenant_key_version");
            assertIndexExists(statement, "lc_form_definition", "uk_lc_form_definition_tenant_key_draft");
            assertIndexExists(statement, "lc_form_instance", "uk_lc_form_instance_tenant_process");
            assertIndexExists(statement, "lc_application", "uk_lc_application_tenant_key");
            assertIndexExists(statement, "lc_application_version", "uk_lc_application_version_tenant_key_version");
            assertIndexExists(statement, "lc_application_version", "uk_lc_application_version_tenant_key_draft");
            assertIndexExists(statement, "lc_application_page", "uk_lc_application_page_version_type");
            assertIndexExists(statement, "lc_application_action", "uk_lc_application_action_version_code");
            assertIndexExists(statement, "lc_form_instance_workflow_audit", "idx_lc_form_instance_workflow_audit_instance");
            assertIndexExists(statement, "lc_form_instance_workflow_audit", "idx_lc_form_instance_workflow_audit_process");
            assertRowCount(statement, "lc_form_definition",
                    "tenant_id = 'GLOBAL' AND form_key = 'expense' AND version = 1 AND status = 'PUBLISHED'", 1);
            assertRowCount(statement, "lc_application_version",
                    "tenant_id = 'GLOBAL' AND app_key = 'expense_report' AND version = 1 AND status = 'PUBLISHED'", 1);
            assertRowCount(statement, "lc_application_page",
                    "application_version_id = 'LC_APPV_EXPENSE_REPORT_001'", 3);
            assertRowCount(statement, "lc_application_action",
                    "application_version_id = 'LC_APPV_EXPENSE_REPORT_001' "
                            + "AND action_code = 'submitAndLaunch' "
                            + "AND action_type = 'SUBMIT_AND_LAUNCH_WORKFLOW' "
                            + "AND process_key = 'expense_report'", 1);

            statement.executeUpdate("""
                    INSERT INTO lc_form_definition(id, tenant_id, form_key, name, version, status)
                    VALUES ('F1', 'T1', 'expense', 'Expense', 1, 'PUBLISHED')
                    """);
            statement.executeUpdate("""
                    INSERT INTO lc_form_definition(id, tenant_id, form_key, name, version, status)
                    VALUES ('F2', 'T2', 'expense', 'Expense', 1, 'PUBLISHED')
                    """);
            statement.executeUpdate("""
                    INSERT INTO lc_form_definition(id, tenant_id, form_key, name, version, status)
                    VALUES ('F3', 'T1', 'expense', 'Expense draft', 2, 'DRAFT')
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO lc_form_definition(id, tenant_id, form_key, name, version, status)
                    VALUES ('F4', 'T1', 'expense', 'Expense draft duplicate', 3, 'DRAFT')
                    """)).isInstanceOf(Exception.class);
        }
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

    private void assertRowCount(Statement statement, String tableName, String whereClause, int expected) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT count(*) FROM %s
                WHERE %s
                """.formatted(tableName, whereClause))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(expected);
        }
    }
}
