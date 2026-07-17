package com.triobase.service.openapi.migration;

import com.triobase.service.openapi.support.PostgreSqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RouteReleasePersistenceIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Test
    void activationCompareAndSetAllowsOneWinnerAndSupportsRollback() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_schema_history_route_release_test")
                .load()
                .migrate();
        seed();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> contenders = List.of(
                activation("release-2", ready, start), activation("release-3", ready, start));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = contenders.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            int updates = futures.get(0).get() + futures.get(1).get();
            assertThat(updates).isEqualTo(1);
        }

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT release_snapshot_id, row_version, policy_version
                     FROM oa_active_release
                     WHERE route_definition_id='route-1' AND environment='PROD'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isIn("release-2", "release-3");
            assertThat(result.getLong(2)).isEqualTo(1L);
            assertThat(result.getLong(3)).isEqualTo(2L);
        }

        try (Connection connection = connection();
             PreparedStatement rollback = connection.prepareStatement("""
                     UPDATE oa_active_release
                     SET release_snapshot_id='release-1', policy_version=3, row_version=row_version+1
                     WHERE route_definition_id='route-1' AND environment='PROD' AND row_version=1
                     """)) {
            assertThat(rollback.executeUpdate()).isEqualTo(1);
        }
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT release_snapshot_id, row_version FROM oa_active_release
                     WHERE route_definition_id='route-1' AND environment='PROD'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("release-1");
            assertThat(result.getLong(2)).isEqualTo(2L);
        }
    }

    private Callable<Integer> activation(String releaseId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            try (Connection connection = connection();
                 PreparedStatement update = connection.prepareStatement("""
                         UPDATE oa_active_release
                         SET release_snapshot_id=?, policy_version=2, row_version=row_version+1
                         WHERE route_definition_id='route-1' AND environment='PROD' AND row_version=0
                         """)) {
                update.setString(1, releaseId);
                ready.countDown();
                start.await();
                return update.executeUpdate();
            }
        };
    }

    private void seed() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO oa_route_definition(
                      id,tenant_id,route_key,display_name,owner_id,lifecycle_state,row_version,created_by,updated_by)
                    VALUES ('route-1','tenant-a','orders.submit','Submit order','team-a','ACTIVE',0,'test','test')
                    """);
            statement.executeUpdate("""
                    INSERT INTO oa_route_version(
                      id,route_definition_id,version_number,environment,lifecycle_state,priority,enabled,
                      route_predicate,execution_mode,row_version,created_by,updated_by)
                    VALUES ('route-v1','route-1',1,'PROD','PUBLISHED',10,true,'{}','SYNCHRONOUS',0,'test','test')
                    """);
        }
        try (Connection connection = connection()) {
            for (int number = 1; number <= 3; number++) {
                try (PreparedStatement release = connection.prepareStatement("""
                        INSERT INTO oa_release_snapshot(
                          id,tenant_id,environment,route_definition_id,route_version_id,release_number,
                          lifecycle_state,pinned_dependencies,snapshot_hash,validation_result,published_by)
                        VALUES (?, 'tenant-a','PROD','route-1','route-v1',?,'PUBLISHED',?,'hash',?,'test')
                        """)) {
                    release.setString(1, "release-" + number);
                    release.setInt(2, number);
                    release.setObject(3, "{\"routeVersionId\":\"route-v1\"}", Types.OTHER);
                    release.setObject(4, "{\"valid\":true}", Types.OTHER);
                    release.executeUpdate();
                }
            }
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO oa_active_release(
                      route_definition_id,environment,release_snapshot_id,policy_version,
                      activated_by,row_version)
                    VALUES ('route-1','PROD','release-1',1,'test',0)
                    """);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
