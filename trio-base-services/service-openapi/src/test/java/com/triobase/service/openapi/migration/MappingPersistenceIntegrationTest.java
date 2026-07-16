package com.triobase.service.openapi.migration;

import com.triobase.service.openapi.support.PostgreSqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingPersistenceIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Test
    void persistsPinnedMappingRulesAndEnforcesTenantIdentity() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_schema_history_mapping_test")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            seedStructure(connection, "canonical", "tenant-a", "canonical-order");
            seedStructure(connection, "external", "tenant-a", "erp-order");
            seedVersion(connection, "canonical-v1", "canonical");
            seedVersion(connection, "external-v1", "external");
            insertMappingSet(connection, "mapping-set", "tenant-a", "order-map");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO oa_mapping_version(
                      id,mapping_set_id,version_number,source_structure_version_id,
                      target_structure_version_id,lifecycle_state,coverage_result,row_version,
                      created_by,updated_by)
                    VALUES ('mapping-v1','mapping-set',1,'canonical-v1','external-v1','DRAFT',?,0,'test','test')
                    """)) {
                statement.setObject(1, "{\"valid\":true}", Types.OTHER);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO oa_mapping_rule(
                      id,mapping_version_id,rule_order,operation_type,source_pointer,target_pointer,
                      operation_config,required_rule,created_by,updated_by)
                    VALUES ('rule-1','mapping-v1',1,'TYPE_CONVERT','/amount','/total',?,true,'test','test')
                    """)) {
                statement.setObject(1, "{\"targetType\":\"number\"}", Types.OTHER);
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                         SELECT operation_config->>'targetType'
                         FROM oa_mapping_rule WHERE id='rule-1'
                         """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("number");
            }

            assertThatThrownBy(() -> insertMappingSet(connection, "mapping-duplicate", "tenant-a", "order-map"))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23505");
            insertMappingSet(connection, "mapping-other-tenant", "tenant-b", "order-map");
        }
    }

    private void seedStructure(Connection connection, String id, String tenantId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO oa_structure(
                  id,tenant_id,namespace,structure_key,display_name,structure_kind,data_format,
                  direction,owner_type,owner_id,lifecycle_state,row_version,created_by,updated_by)
                VALUES (?,?,'test',?,?,'CANONICAL','JSON','BIDIRECTIONAL','DOMAIN','test','ACTIVE',0,'test','test')
                """)) {
            statement.setString(1, id);
            statement.setString(2, tenantId);
            statement.setString(3, key);
            statement.setString(4, key);
            statement.executeUpdate();
        }
    }

    private void seedVersion(Connection connection, String id, String structureId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO oa_structure_version(
                  id,structure_id,version_number,compatibility_line,lifecycle_state,schema_content,
                  schema_hash,row_version,created_by,updated_by)
                VALUES (?,?,1,1,'PUBLISHED',?,'hash',0,'test','test')
                """)) {
            statement.setString(1, id);
            statement.setString(2, structureId);
            statement.setObject(3, "{\"type\":\"object\",\"properties\":{}}", Types.OTHER);
            statement.executeUpdate();
        }
    }

    private void insertMappingSet(Connection connection, String id, String tenantId, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO oa_mapping_set(
                  id,tenant_id,mapping_key,display_name,direction,canonical_structure_id,
                  external_structure_id,owner_id,lifecycle_state,row_version,created_by,updated_by)
                VALUES (?,?,?,?,'CANONICAL_TO_EXTERNAL','canonical','external','test','ACTIVE',0,'test','test')
                """)) {
            statement.setString(1, id);
            statement.setString(2, tenantId);
            statement.setString(3, key);
            statement.setString(4, key);
            statement.executeUpdate();
        }
    }
}
