package com.triobase.service.workflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.temporal.test-server.enabled=true",
                "spring.temporal.start-workers=false"
        })
class BusinessObjectCatalogSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("triobase_test")
            .withUsername("triobase")
            .withPassword("triobase");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTenantRows() {
        jdbcTemplate.update("DELETE FROM wf_biz_object WHERE tenant_id LIKE 'TENANT_TEST_%'");
    }

    @Test
    void migrationSeedsPublishedExpenseReportCatalog() {
        Integer objectCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM wf_biz_object
                WHERE tenant_id = 'GLOBAL'
                  AND type_code = 'expense_report'
                  AND status = 'PUBLISHED'
                """, Integer.class);
        assertEquals(1, objectCount);

        assertEquals(4, countChildren("wf_biz_object_status"));
        assertTrue(countChildren("wf_biz_object_permission") >= 5);
        assertTrue(countChildren("wf_biz_object_action") >= 3);
        assertTrue(countChildren("wf_biz_object_event") >= 2);
        assertTrue(countChildren("wf_biz_object_agent_action") >= 2);
    }

    @Test
    void tenantOverrideCanCoexistWithGlobalCatalog() {
        jdbcTemplate.update("""
                INSERT INTO wf_biz_object
                    (id, tenant_id, type_code, display_name, service_code, version, status, source_object_id)
                VALUES (?, ?, ?, ?, ?, 1, 'PUBLISHED', ?)
                """, "BIZ_TENANT_OVERRIDE", "TENANT_TEST_A", "expense_report",
                "租户报销单", "expense-service", "BIZ_EXPENSE_REPORT");

        Integer effectiveCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM wf_biz_object
                WHERE type_code = 'expense_report'
                  AND tenant_id IN ('GLOBAL', 'TENANT_TEST_A')
                  AND status = 'PUBLISHED'
                """, Integer.class);
        assertEquals(2, effectiveCount);
    }

    @Test
    void databasePreventsDuplicateCatalogVersionAndChildCodes() {
        jdbcTemplate.update("""
                INSERT INTO wf_biz_object
                    (id, tenant_id, type_code, display_name, service_code, version, status)
                VALUES (?, ?, ?, ?, ?, 1, 'PUBLISHED')
                """, "BIZ_DUP_A", "TENANT_TEST_DUP", "ticket", "工单", "ticket-service");

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO wf_biz_object
                    (id, tenant_id, type_code, display_name, service_code, version, status)
                VALUES (?, ?, ?, ?, ?, 1, 'PUBLISHED')
                """, "BIZ_DUP_B", "TENANT_TEST_DUP", "ticket", "重复工单", "ticket-service"));

        jdbcTemplate.update("""
                INSERT INTO wf_biz_object_status
                    (id, object_id, status_code, display_name)
                VALUES (?, ?, ?, ?)
                """, "BIZ_DUP_STATUS_A", "BIZ_DUP_A", "OPEN", "打开");

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO wf_biz_object_status
                    (id, object_id, status_code, display_name)
                VALUES (?, ?, ?, ?)
                """, "BIZ_DUP_STATUS_B", "BIZ_DUP_A", "OPEN", "重复打开"));
    }

    @Test
    void offlineCatalogRecordDoesNotDeletePublishedSnapshots() {
        jdbcTemplate.update("""
                INSERT INTO wf_biz_object
                    (id, tenant_id, type_code, display_name, service_code, version, status)
                VALUES (?, ?, ?, ?, ?, 1, 'PUBLISHED')
                """, "BIZ_OFFLINE_A", "TENANT_TEST_OFFLINE", "contract", "合同", "contract-service");

        jdbcTemplate.update("""
                UPDATE wf_biz_object
                SET status = 'OFFLINE', offline_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, "BIZ_OFFLINE_A");

        String status = jdbcTemplate.queryForObject("""
                SELECT status FROM wf_biz_object WHERE id = ?
                """, String.class, "BIZ_OFFLINE_A");
        assertEquals("OFFLINE", status);
    }

    @Test
    void closureRuntimeTablesEnforceOutcomeAndEffectUniqueness() {
        jdbcTemplate.update("""
                INSERT INTO wf_process_instance
                    (id, process_package_id, process_key, process_name, version, status,
                     initiator_id, initiator_name)
                VALUES (?, ?, ?, ?, 1, 'COMPLETED', ?, ?)
                """, "pi-test", "PKG001", "expense_report", "费用报销", "starter", "Starter");

        jdbcTemplate.update("""
                INSERT INTO wf_process_outcome
                    (id, process_instance_id, outcome_version, process_package_id, process_key,
                     process_version, business_type, business_id, outcome_status)
                VALUES (?, ?, 1, ?, ?, 1, ?, ?, 'APPROVED')
                """, "OUTCOME_TEST_A", "pi-test", "PKG001", "expense_report",
                "expense_report", "ER001");

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO wf_process_outcome
                    (id, process_instance_id, outcome_version, process_package_id, process_key,
                     process_version, business_type, business_id, outcome_status)
                VALUES (?, ?, 1, ?, ?, 1, ?, ?, 'APPROVED')
                """, "OUTCOME_TEST_B", "pi-test", "PKG001", "expense_report",
                "expense_report", "ER001"));

        jdbcTemplate.update("""
                INSERT INTO wf_process_closure
                    (id, outcome_id, process_instance_id, business_type, business_id)
                VALUES (?, ?, ?, ?, ?)
                """, "CLOSURE_TEST_A", "OUTCOME_TEST_A", "pi-test",
                "expense_report", "ER001");

        jdbcTemplate.update("""
                INSERT INTO wf_closure_effect
                    (id, closure_id, effect_key, effect_type, idempotency_key)
                VALUES (?, ?, ?, 'BUSINESS_STATUS_UPDATE', ?)
                """, "EFFECT_TEST_A", "CLOSURE_TEST_A", "update-status",
                "pi-test:APPROVED:update-status");

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO wf_closure_effect
                    (id, closure_id, effect_key, effect_type, idempotency_key)
                VALUES (?, ?, ?, 'BUSINESS_STATUS_UPDATE', ?)
                """, "EFFECT_TEST_B", "CLOSURE_TEST_A", "update-status",
                "pi-test:APPROVED:update-status-2"));
    }

    private int countChildren(String table) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s WHERE object_id = 'BIZ_EXPENSE_REPORT'
                """.formatted(table), Integer.class);
        return count == null ? 0 : count;
    }
}
