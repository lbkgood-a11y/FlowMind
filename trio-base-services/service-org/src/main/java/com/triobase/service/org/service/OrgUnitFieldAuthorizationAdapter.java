package com.triobase.service.org.service;

import com.triobase.common.core.auth.FieldAuthorizationAdapter;
import com.triobase.common.core.auth.FieldEnforcementManifest;
import com.triobase.common.core.auth.FieldMaskHelper;
import com.triobase.common.core.auth.FieldRule;
import com.triobase.service.org.dto.OrgTreeNodeResponse;
import com.triobase.service.org.entity.SysOrgUnit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OrgUnitFieldAuthorizationAdapter implements FieldAuthorizationAdapter<SysOrgUnit> {

    public static final String RESOURCE_CODE = "ORG_UNIT";
    public static final Set<String> FIELD_KEYS = Set.of("unitCode", "unitName", "unitType", "status");

    @Override
    public FieldEnforcementManifest manifest() {
        return new FieldEnforcementManifest("service-org", RESOURCE_CODE, "BUSINESS_OBJECT", FIELD_KEYS,
                true, true, true, Set.of("LIST", "TREE", "CREATE", "UPDATE"));
    }

    @Override
    public Map<String, Object> filterRead(SysOrgUnit source, List<? extends FieldRule> rules) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("unitCode", source.getUnitCode());
        values.put("unitName", source.getUnitName());
        values.put("unitType", source.getUnitType());
        values.put("status", source.getStatus());
        return FieldMaskHelper.applyReadRules(values, rules);
    }

    public SysOrgUnit applyRead(SysOrgUnit source, List<? extends FieldRule> rules) {
        Map<String, Object> values = filterRead(source, rules);
        source.setUnitCode(asString(values.get("unitCode")));
        source.setUnitName(asString(values.get("unitName")));
        source.setUnitType(asString(values.get("unitType")));
        source.setStatus(asShort(values.get("status")));
        return source;
    }

    public OrgTreeNodeResponse applyRead(OrgTreeNodeResponse source, List<? extends FieldRule> rules) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("unitCode", source.getUnitCode());
        values.put("unitName", source.getUnitName());
        values.put("unitType", source.getUnitType());
        values.put("status", source.getStatus());
        values = FieldMaskHelper.applyReadRules(values, rules);
        source.setUnitCode(asString(values.get("unitCode")));
        source.setUnitName(asString(values.get("unitName")));
        source.setUnitType(asString(values.get("unitType")));
        source.setStatus(asShort(values.get("status")));
        return source;
    }

    @Override
    public void validateWrite(Map<String, Object> changes, List<? extends FieldRule> rules) {
        FieldMaskHelper.assertWritableFields(changes, rules);
    }

    private String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private Short asShort(Object value) {
        return value instanceof Number number ? number.shortValue() : null;
    }
}
