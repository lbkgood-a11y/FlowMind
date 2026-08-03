package com.triobase.service.auth.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerDataScopeContractMigrationTest {

    @Test
    void registersEveryStaticRequireDataScopeContractAsVerified() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V96__register_owner_data_scope_contracts.sql")) {
            assertTrue(stream != null, "Owner data-scope migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertContract(migration, "USER", "QUERY");
        assertContract(migration, "FORM_INSTANCE", "QUERY");
        assertContract(migration, "FORM_INSTANCE", "EXPORT");
        assertContract(migration, "BUSINESS_TIMELINE", "QUERY");
        assertTrue(migration.contains("data_scope_supported"));
        assertTrue(migration.contains("data_scope_enforced"));
    }

    @Test
    void restoresDefaultUserSelfPolicyAfterLegacyPageRelease() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V97__restore_user_self_data_policy.sql")) {
            assertTrue(stream != null, "USER self-policy repair migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("'R003', 'USER', 'QUERY'"));
        assertTrue(migration.contains("'ADMIN', 'SELF'"));
    }

    @Test
    void grantsDefaultUserOwnerQueryForFieldEnforcement() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V98__grant_default_user_query_contract.sql")) {
            assertTrue(stream != null, "USER Owner-query grant migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("'R003', 'USER', 'QUERY'"));
        assertTrue(migration.contains("'ALLOW', 1"));
    }

    @Test
    void registersOrganizationOwnerQueryContract() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V99__register_org_unit_query_contract.sql")) {
            assertTrue(stream != null, "ORG_UNIT Owner-query migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("'default', 'ORG_UNIT', 'QUERY', 'READ'"));
        assertTrue(migration.contains("data_scope_supported = 1"));
        assertTrue(migration.contains("data_scope_enforced = 1"));
    }

    private void assertContract(String migration, String resourceCode, String actionCode) {
        assertTrue(migration.contains("('" + resourceCode + "', '" + actionCode + "'"),
                () -> "Missing Owner data-scope contract " + resourceCode + ':' + actionCode);
    }
}
