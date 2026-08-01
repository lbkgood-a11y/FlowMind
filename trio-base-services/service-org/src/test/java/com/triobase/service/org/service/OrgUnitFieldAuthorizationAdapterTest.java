package com.triobase.service.org.service;

import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.service.org.entity.SysOrgUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrgUnitFieldAuthorizationAdapterTest {

    private final OrgUnitFieldAuthorizationAdapter adapter = new OrgUnitFieldAuthorizationAdapter();

    @Test
    void masksAndHidesFieldsAtOwnerBoundary() {
        SysOrgUnit unit = new SysOrgUnit();
        unit.setUnitCode("FINANCE-CENTER");
        unit.setUnitName("财务中心");

        adapter.applyRead(unit, List.of(
                rule("unitCode", "MASKED", "EDITABLE", "LAST4"),
                rule("unitName", "HIDDEN", "EDITABLE", null)));

        assertThat(unit.getUnitCode()).endsWith("NTER").startsWith("**********");
        assertThat(unit.getUnitName()).isNull();
    }

    @Test
    void rejectsDeniedOrgUnitWrite() {
        assertThatThrownBy(() -> adapter.validateWrite(
                Map.of("unitName", "新名称"),
                List.of(rule("unitName", "VISIBLE", "READ_ONLY", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitName");
    }

    private AuthzFieldRule rule(String key, String readMode, String writeMode, String strategy) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(key);
        rule.setReadMode(readMode);
        rule.setWriteMode(writeMode);
        rule.setMaskStrategy(strategy);
        return rule;
    }
}
