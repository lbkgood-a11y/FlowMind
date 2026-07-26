package com.triobase.service.apiruntime.mapping;

import com.triobase.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RuntimeValueMapLookup {

    private final JdbcTemplate jdbcTemplate;

    public String lookup(String versionId, String value, boolean canonicalToExternal) {
        VersionPolicy policy = versionPolicy(versionId);
        List<Entry> entries = jdbcTemplate.query("""
                SELECT canonical_value, external_value
                FROM oa_value_map_entry
                WHERE value_map_version_id = ?
                ORDER BY entry_order
                """, (rs, rowNum) -> new Entry(
                rs.getString("canonical_value"),
                rs.getString("external_value")), versionId);
        for (Entry entry : entries) {
            String candidate = canonicalToExternal ? entry.canonicalValue() : entry.externalValue();
            if (matches(candidate, value, policy.caseSensitive())) {
                return canonicalToExternal ? entry.externalValue() : entry.canonicalValue();
            }
        }
        return switch (policy.unmappedPolicy()) {
            case "PASS_THROUGH" -> value;
            case "USE_DEFAULT" -> canonicalToExternal
                    ? policy.defaultExternalValue() : policy.defaultCanonicalValue();
            case "FAIL" -> throw new BizException(42220, "OPENAPI_VALUE_MAP_VALUE_UNMAPPED");
            default -> throw new BizException(40922, "OPENAPI_VALUE_MAP_POLICY_UNSUPPORTED");
        };
    }

    private VersionPolicy versionPolicy(String versionId) {
        List<VersionPolicy> policies = jdbcTemplate.query("""
                SELECT lifecycle_state, case_sensitive, unmapped_policy,
                       default_canonical_value, default_external_value
                FROM oa_value_map_version
                WHERE id = ?
                """, (rs, rowNum) -> new VersionPolicy(
                rs.getString("lifecycle_state"),
                rs.getBoolean("case_sensitive"),
                rs.getString("unmapped_policy"),
                rs.getString("default_canonical_value"),
                rs.getString("default_external_value")), versionId);
        if (policies.isEmpty()) {
            throw new BizException(40422, "OPENAPI_VALUE_MAP_VERSION_NOT_FOUND");
        }
        VersionPolicy policy = policies.getFirst();
        if (!"PUBLISHED".equals(policy.lifecycleState())) {
            throw new BizException(40922, "OPENAPI_VALUE_MAP_NOT_PUBLISHED");
        }
        return policy;
    }

    private boolean matches(String candidate, String value, boolean caseSensitive) {
        if (candidate == null || value == null) {
            return false;
        }
        return caseSensitive ? candidate.equals(value) : candidate.equalsIgnoreCase(value);
    }

    private record VersionPolicy(
            String lifecycleState,
            boolean caseSensitive,
            String unmappedPolicy,
            String defaultCanonicalValue,
            String defaultExternalValue) {
    }

    private record Entry(String canonicalValue, String externalValue) {
    }
}
