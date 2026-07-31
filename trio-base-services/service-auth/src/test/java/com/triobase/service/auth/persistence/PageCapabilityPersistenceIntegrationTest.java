package com.triobase.service.auth.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class PageCapabilityPersistenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateFoundation() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_menu (id VARCHAR(32) PRIMARY KEY)");
            statement.execute("CREATE TABLE sys_role (id VARCHAR(32) PRIMARY KEY)");
            statement.execute("CREATE TABLE sys_auth_version (version_key VARCHAR(64) PRIMARY KEY, "
                    + "version_value BIGINT NOT NULL DEFAULT 1, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V78__page_capability_authorization_foundation.sql"));
            statement.execute("INSERT INTO sys_menu(id) VALUES ('M_USER')");
            statement.execute("INSERT INTO sys_role(id) VALUES ('R_TEST')");
            statement.execute("INSERT INTO sys_auth_page_catalog "
                    + "(id, tenant_id, catalog_code, catalog_version, source_type, manifest_hash, lifecycle_status) "
                    + "VALUES ('CAT_T1', 'T1', 'SYSTEM', 1, 'SYSTEM_MANIFEST', 'hash-1', 'ACTIVE')");
            statement.execute("INSERT INTO sys_auth_page_catalog "
                    + "(id, tenant_id, catalog_code, catalog_version, source_type, manifest_hash, lifecycle_status) "
                    + "VALUES ('CAT_T2', 'T2', 'SYSTEM', 1, 'SYSTEM_MANIFEST', 'hash-2', 'ACTIVE')");
            statement.execute("INSERT INTO sys_auth_page_capability "
                    + "(id, tenant_id, catalog_id, menu_id, page_code, page_name, capability_code, capability_name, "
                    + "capability_category, readiness_status) VALUES "
                    + "('CAP_ACCESS', 'T1', 'CAT_T1', 'M_USER', 'USER', 'User Management', "
                    + "'USER.ACCESS', 'Enter User Management', 'ACCESS', 'READY')");
            statement.execute("INSERT INTO sys_auth_page_capability "
                    + "(id, tenant_id, catalog_id, menu_id, page_code, page_name, capability_code, capability_name, "
                    + "capability_category, readiness_status) VALUES "
                    + "('CAP_T2_READ', 'T2', 'CAT_T2', 'M_USER', 'USER', 'User Management', "
                    + "'USER.READ', 'View users', 'READ', 'READY')");
        }
    }

    @Test
    void rejectsDuplicateCapabilityCodeWithinCatalog() throws SQLException {
        try (Connection connection = connection()) {
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO sys_auth_page_capability "
                            + "(id, tenant_id, catalog_id, page_code, page_name, capability_code, capability_name, "
                            + "capability_category, readiness_status) VALUES "
                            + "('CAP_DUP', 'T1', 'CAT_T1', 'USER', 'User Management', "
                            + "'USER.ACCESS', 'Duplicate', 'ACCESS', 'READY')"));
        }
    }

    @Test
    void rejectsCrossTenantCatalogReference() throws SQLException {
        try (Connection connection = connection()) {
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO sys_role_auth_draft "
                            + "(id, tenant_id, role_id, catalog_id, draft_status, intent_version) "
                            + "VALUES ('DRAFT_T2', 'T2', 'R_TEST', 'CAT_T1', 'DRAFT', 1)"));
        }
    }

    @Test
    void rejectsCrossTenantCapabilityDependency() throws SQLException {
        try (Connection connection = connection()) {
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO sys_auth_page_capability_dependency "
                            + "(id, tenant_id, capability_id, required_capability_id) "
                            + "VALUES ('DEP_CROSS', 'T1', 'CAP_ACCESS', 'CAP_T2_READ')"));
        }
    }

    @Test
    void publishedReleaseIsImmutableAndCanBeSelectedAsActive() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            execute(connection, "INSERT INTO sys_role_auth_release "
                    + "(id, tenant_id, role_id, catalog_id, release_number, intent_version, catalog_version, "
                    + "validation_hash, intent_snapshot, compiled_snapshot, business_summary, published_by) VALUES "
                    + "('REL_1', 'T1', 'R_TEST', 'CAT_T1', 1, 1, 1, 'validated', '{}', '{}', "
                    + "'Can enter User Management', 'ADMIN')");
            execute(connection, "INSERT INTO sys_role_auth_active_release "
                    + "(tenant_id, role_id, release_id, activated_by) VALUES ('T1', 'R_TEST', 'REL_1', 'ADMIN')");

            try (ResultSet result = statement.executeQuery("SELECT release_id FROM sys_role_auth_active_release "
                    + "WHERE tenant_id = 'T1' AND role_id = 'R_TEST'")) {
                result.next();
                assertEquals("REL_1", result.getString(1));
            }
            assertThrows(SQLException.class, () -> execute(connection,
                    "UPDATE sys_role_auth_release SET business_summary = 'changed' WHERE id = 'REL_1'"));
            assertThrows(SQLException.class, () -> execute(connection,
                    "DELETE FROM sys_role_auth_release WHERE id = 'REL_1'"));
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
