package com.triobase.common.core.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest {

    @Test
    void denyAllOverridesAllAndSelfAllows() {
        DataScope scope = scope(
                policy("ALLOW", dimension("ALL")),
                policy("ALLOW", dimension("SELF")),
                policy("DENY", dimension("ALL")));

        assertThat(scope.allowsAll()).isFalse();
        assertThat(scope.allowsSelf()).isFalse();
        assertThat(scope.deniesAll()).isTrue();
    }

    @Test
    void restrictiveScopeNeverAllowsRows() {
        DataScope scope = DataScope.restrictive("user-1", "FORM:EXPENSE", "VIEW");

        assertThat(scope.allowsAll()).isFalse();
        assertThat(scope.allowsSelf()).isFalse();
        assertThat(scope.deniesAll()).isTrue();
    }

    private DataScope scope(DataScope.Policy... policies) {
        return new DataScope("user-1", "FORM:EXPENSE", "VIEW", false,
                true, List.of(), List.of(policies));
    }

    private DataScope.Policy policy(String effect, DataScope.Dimension... dimensions) {
        return new DataScope.Policy("role-1", effect, "OR", List.of(dimensions));
    }

    private DataScope.Dimension dimension(String scopeType) {
        return new DataScope.Dimension("ROW", scopeType, List.of());
    }
}
