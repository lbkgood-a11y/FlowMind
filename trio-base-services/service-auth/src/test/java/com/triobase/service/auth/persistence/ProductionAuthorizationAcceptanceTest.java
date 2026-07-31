package com.triobase.service.auth.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "triobase.acceptance.required", matches = "true")
class ProductionAuthorizationAcceptanceTest {

    private static PostgreSQLContainer<?> postgres;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static String schemaName;
    private static boolean schemaCreated;

    @BeforeAll
    static void migrateRealPostgres() {
        jdbcUrl = System.getProperty("triobase.acceptance.jdbc-url");
        username = System.getProperty("triobase.acceptance.username", "postgres");
        password = System.getProperty("triobase.acceptance.password", "postgres");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            // Deliberately not disabled without Docker: the production profile must fail, never skip.
            postgres.start();
            jdbcUrl = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        }
        schemaName = "auth_acceptance_" + UUID.randomUUID().toString().replace("-", "");
        try {
            createIsolatedSchema();
            migrateOwnerServiceSchemas();
            migrate("classpath:db/migration", "flyway_schema_history_auth");
        } catch (RuntimeException | SQLException exception) {
            cleanupAfterFailedSetup(exception);
            throw new IllegalStateException("PostgreSQL production acceptance setup failed", exception);
        }
    }

    @AfterAll
    static void cleanupDatabase() throws SQLException {
        try {
            dropIsolatedSchema();
        } finally {
            if (postgres != null) {
                postgres.stop();
                postgres = null;
            }
        }
    }

    @Test
    void migrationsAndTenantEvidenceEnforceProductionCutoverInvariants() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertTrue(tableExists(connection, "sys_auth_page_catalog"));
            assertTrue(columnExists(connection, "sys_role_auth_draft", "migration_expansion_acknowledged"));
            statement.execute("INSERT INTO sys_role(id, tenant_id, role_code, role_name, status) "
                    + "VALUES ('ACC_ROLE_T1', 'ACC_T1', 'IMPLEMENTER', 'Implementer T1', 1), "
                    + "('ACC_ROLE_T2', 'ACC_T2', 'IMPLEMENTER', 'Implementer T2', 1)");
            statement.execute("INSERT INTO sys_auth_page_catalog "
                    + "(id, tenant_id, catalog_code, catalog_version, source_type, manifest_hash, lifecycle_status) VALUES "
                    + "('ACC_CAT_T1', 'ACC_T1', 'SYSTEM', 1, 'SYSTEM_MANIFEST', 'acc-hash-t1', 'ACTIVE'), "
                    + "('ACC_CAT_T2', 'ACC_T2', 'SYSTEM', 1, 'SYSTEM_MANIFEST', 'acc-hash-t2', 'ACTIVE')");
            statement.execute("INSERT INTO sys_role_auth_release "
                    + "(id, tenant_id, role_id, catalog_id, release_number, intent_version, catalog_version, "
                    + "validation_hash, intent_snapshot, compiled_snapshot, business_summary, published_by) VALUES "
                    + "('ACC_REL_T1', 'ACC_T1', 'ACC_ROLE_T1', 'ACC_CAT_T1', 1, 1, 1, 'h1', '{}', '{}', 'T1', 'CI'), "
                    + "('ACC_REL_T2', 'ACC_T2', 'ACC_ROLE_T2', 'ACC_CAT_T2', 1, 1, 1, 'h2', '{}', '{}', 'T2', 'CI')");
            statement.execute("INSERT INTO sys_role_auth_compiled_evidence "
                    + "(id, tenant_id, release_id, capability_code, projection_type, projection_key, "
                    + "resource_code, action_code, effect, projection_snapshot) VALUES "
                    + "('ACC_EV_T1', 'ACC_T1', 'ACC_REL_T1', 'USER.READ', 'GRANT', 'users:get', "
                    + "'/api/v1/users', 'GET', 'ALLOW', '{}'), "
                    + "('ACC_EV_T2', 'ACC_T2', 'ACC_REL_T2', 'USER.READ', 'GRANT', 'users:get', "
                    + "'/api/v1/users', 'GET', 'ALLOW', '{}')");

            assertThrows(SQLException.class, () -> execute(connection,
                    "UPDATE sys_role_auth_release SET business_summary='mutated' WHERE id='ACC_REL_T1'"));
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO sys_role_auth_active_release(tenant_id, role_id, release_id, activated_by) "
                            + "VALUES ('ACC_T2', 'ACC_ROLE_T2', 'ACC_REL_T1', 'CI')"));
            try (ResultSet result = statement.executeQuery("SELECT count(DISTINCT tenant_id) "
                    + "FROM sys_role_auth_compiled_evidence WHERE id IN ('ACC_EV_T1','ACC_EV_T2')")) {
                result.next();
                assertEquals(2, result.getInt(1));
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, schemaName + "." + table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name=? AND column_name=?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void createIsolatedSchema() throws SQLException {
        assertSafeSchemaName();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + quotedSchemaName());
            schemaCreated = true;
        }
    }

    private static void migrateOwnerServiceSchemas() {
        Path servicesDirectory = locateServicesDirectory();
        migrate(filesystemLocation(servicesDirectory.resolve("service-lowcode")),
                "flyway_schema_history_lowcode");
        migrate(filesystemLocation(servicesDirectory.resolve("service-workflow-engine")),
                "flyway_schema_history_workflow");
    }

    private static void migrate(String location, String historyTable) {
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(location)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .table(historyTable)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .validateMigrationNaming(true)
                .load()
                .migrate();
    }

    private static Path locateServicesDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("trio-base-services");
            if (Files.isDirectory(candidate.resolve("service-lowcode"))
                    && Files.isDirectory(candidate.resolve("service-workflow-engine"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Production acceptance requires the TrioBase service source tree");
    }

    private static String filesystemLocation(Path serviceDirectory) {
        Path migrations = serviceDirectory.resolve("src/main/resources/db/migration");
        if (!Files.isDirectory(migrations)) {
            throw new IllegalStateException("Migration directory is missing: " + migrations);
        }
        return "filesystem:" + migrations.toString().replace('\\', '/');
    }

    private static void dropIsolatedSchema() throws SQLException {
        if (!schemaCreated) {
            return;
        }
        assertSafeSchemaName();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + quotedSchemaName() + " CASCADE");
            schemaCreated = false;
        }
    }

    private static void cleanupAfterFailedSetup(Exception original) {
        try {
            dropIsolatedSchema();
        } catch (SQLException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        } finally {
            if (postgres != null) {
                postgres.stop();
                postgres = null;
            }
        }
    }

    private static void assertSafeSchemaName() {
        if (schemaName == null || !schemaName.matches("auth_acceptance_[a-f0-9]{32}")) {
            throw new IllegalStateException("Refusing to manage an unsafe acceptance schema name");
        }
    }

    private static String quotedSchemaName() {
        return '"' + schemaName + '"';
    }

    private static Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
        try {
            connection.setSchema(schemaName);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
