package com.triobase.service.apiruntime.resolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.openapi.dto.CompiledRouteRelease;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.resolution.ReleaseResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuntimeReleaseResolver implements ReleaseResolver {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CompiledRouteRelease resolveActive(String tenantId, String routeKey, Environment environment) {
        List<CompiledRouteRelease> releases = jdbcTemplate.query("""
                SELECT rd.tenant_id,
                       rs.environment,
                       rd.route_key,
                       rd.id AS route_id,
                       rs.route_version_id,
                       rs.id AS release_id,
                       ar.policy_version,
                       rs.snapshot_hash,
                       rs.pinned_dependencies::text AS pinned_dependencies
                FROM oa_route_definition rd
                JOIN oa_active_release ar ON ar.route_definition_id = rd.id
                JOIN oa_release_snapshot rs ON rs.id = ar.release_snapshot_id
                WHERE rd.route_key = ?
                  AND ar.environment = ?
                  AND ((? IS NULL AND rd.tenant_id IS NULL) OR rd.tenant_id = ?)
                LIMIT 1
                """, this::mapRelease, routeKey, environment.name(), normalizeTenant(tenantId), normalizeTenant(tenantId));
        if (releases.isEmpty()) {
            throw new BizException(40445, "OPENAPI_ACTIVE_RELEASE_NOT_FOUND");
        }
        return releases.getFirst();
    }

    private CompiledRouteRelease mapRelease(ResultSet rs, int rowNum) throws SQLException {
        return new CompiledRouteRelease(
                rs.getString("tenant_id"),
                Environment.valueOf(rs.getString("environment")),
                rs.getString("route_key"),
                rs.getString("route_id"),
                rs.getString("route_version_id"),
                rs.getString("release_id"),
                rs.getLong("policy_version"),
                rs.getString("snapshot_hash"),
                parseJson(rs.getString("pinned_dependencies")));
    }

    private JsonNode parseJson(String value) {
        try {
            return StringUtils.hasText(value) ? objectMapper.readTree(value) : objectMapper.createObjectNode();
        } catch (Exception exception) {
            throw new BizException(40945, "OPENAPI_RELEASE_SNAPSHOT_INVALID");
        }
    }

    private String normalizeTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : null;
    }
}
