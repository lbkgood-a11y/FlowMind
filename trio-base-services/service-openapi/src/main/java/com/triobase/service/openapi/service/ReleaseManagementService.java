package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ActiveRelease;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.MappingRule;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.OrchestrationVersion;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.entity.ValueMapVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.dto.ReleaseSnapshotResponse;
import com.triobase.service.openapi.infrastructure.mapper.ActiveReleaseMapper;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleaseManagementService {

    private final RouteDefinitionMapper routeDefinitionMapper;
    private final RouteVersionMapper routeVersionMapper;
    private final ConnectorVersionMapper connectorVersionMapper;
    private final MappingVersionMapper mappingVersionMapper;
    private final MappingRuleMapper mappingRuleMapper;
    private final ValueMapVersionMapper valueMapVersionMapper;
    private final OrchestrationVersionMapper orchestrationVersionMapper;
    private final ReleaseSnapshotMapper releaseSnapshotMapper;
    private final ActiveReleaseMapper activeReleaseMapper;
    private final CompiledReleaseCache cache;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;
    private final RoutePlanCompiler routePlanCompiler;

    @Transactional
    public ReleaseSnapshotResponse publish(String routeVersionId, String releaseNotes) {
        RouteVersion routeVersion = requirePublishedRouteVersion(routeVersionId);
        RouteDefinition route = requireRoute(routeVersion.getRouteDefinitionId());
        ObjectNode pinned = buildPinnedDependencies(routeVersion);
        ObjectNode validation = objectMapper.createObjectNode().put("valid", true)
                .put("validatedAt", LocalDateTime.now().toString());
        releaseSnapshotMapper.lockReleaseSeries(route.getId() + ':' + routeVersion.getEnvironment());
        Integer releaseNumber = releaseSnapshotMapper.nextReleaseNumber(
                route.getId(), routeVersion.getEnvironment().name());
        ReleaseSnapshot snapshot = new ReleaseSnapshot();
        snapshot.setId(UlidGenerator.nextUlid());
        snapshot.setTenantId(route.getTenantId());
        snapshot.setEnvironment(routeVersion.getEnvironment());
        snapshot.setRouteDefinitionId(route.getId());
        snapshot.setRouteVersionId(routeVersionId);
        snapshot.setReleaseNumber(releaseNumber);
        snapshot.setLifecycleState(VersionLifecycleState.PUBLISHED);
        snapshot.setPinnedDependencies(pinned);
        snapshot.setSnapshotHash(hash(pinned));
        snapshot.setValidationResult(validation);
        snapshot.setReleaseNotes(StringUtils.hasText(releaseNotes) ? releaseNotes.trim() : null);
        snapshot.setPublishedBy(operator());
        snapshot.setPublishedAt(LocalDateTime.now());
        releaseSnapshotMapper.insert(snapshot);
        auditService.success("RELEASE_PUBLISHED", "RELEASE", snapshot.getId(), validation);
        return response(snapshot);
    }

    @Transactional
    public CompiledRouteRelease activate(String releaseId) {
        ReleaseSnapshot snapshot = requireRelease(releaseId);
        if (snapshot.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40945, "OPENAPI_RELEASE_NOT_ACTIVATABLE");
        }
        RouteDefinition route = requireRoute(snapshot.getRouteDefinitionId());
        ActiveRelease current = activeReleaseMapper.find(route.getId(), snapshot.getEnvironment().name());
        long policyVersion = current == null ? 1L : current.getPolicyVersion() + 1;
        LocalDateTime now = LocalDateTime.now();
        int changed;
        if (current == null) {
            ActiveRelease active = new ActiveRelease();
            active.setRouteDefinitionId(route.getId());
            active.setEnvironment(snapshot.getEnvironment());
            active.setReleaseSnapshotId(releaseId);
            active.setPolicyVersion(policyVersion);
            active.setActivatedBy(operator());
            active.setActivatedAt(now);
            active.setRowVersion(0L);
            changed = activeReleaseMapper.insertIfAbsent(active);
        } else {
            changed = activeReleaseMapper.compareAndSet(route.getId(), snapshot.getEnvironment().name(), releaseId,
                    policyVersion, operator(), now, current.getRowVersion());
        }
        if (changed != 1) {
            throw new BizException(40946, "OPENAPI_RELEASE_ACTIVATION_CONFLICT");
        }
        CompiledRouteRelease compiled = compiled(route, snapshot, policyVersion);
        afterCommit(() -> {
            cache.evict(route.getTenantId(), snapshot.getEnvironment(), route.getRouteKey());
            cache.put(compiled);
        });
        auditService.success("RELEASE_ACTIVATED", "RELEASE", releaseId,
                objectMapper.createObjectNode().put("policyVersion", policyVersion));
        return compiled;
    }

    @Transactional
    public CompiledRouteRelease rollback(String routeId, Environment environment, String targetReleaseId) {
        ReleaseSnapshot target = requireRelease(targetReleaseId);
        if (!target.getRouteDefinitionId().equals(routeId) || target.getEnvironment() != environment) {
            throw new BizException(40045, "OPENAPI_ROLLBACK_TARGET_INVALID");
        }
        CompiledRouteRelease release = activate(targetReleaseId);
        auditService.success("RELEASE_ROLLED_BACK", "ROUTE", routeId,
                objectMapper.createObjectNode().put("targetReleaseId", targetReleaseId));
        return release;
    }

    @Transactional
    public ReleaseSnapshotResponse deprecate(String releaseId) {
        ReleaseSnapshot snapshot = requireRelease(releaseId);
        RouteDefinition route = requireRoute(snapshot.getRouteDefinitionId());
        ActiveRelease active = activeReleaseMapper.find(route.getId(), snapshot.getEnvironment().name());
        if (active != null && releaseId.equals(active.getReleaseSnapshotId())) {
            throw new BizException(40945, "OPENAPI_ACTIVE_RELEASE_CANNOT_BE_DEPRECATED");
        }
        snapshot.setLifecycleState(VersionLifecycleState.DEPRECATED);
        snapshot.setDeprecatedAt(LocalDateTime.now());
        releaseSnapshotMapper.updateById(snapshot);
        auditService.success("RELEASE_DEPRECATED", "RELEASE", releaseId, objectMapper.createObjectNode());
        return response(snapshot);
    }

    @Transactional
    public ReleaseSnapshotResponse archive(String releaseId) {
        ReleaseSnapshot snapshot = requireRelease(releaseId);
        RouteDefinition route = requireRoute(snapshot.getRouteDefinitionId());
        ActiveRelease active = activeReleaseMapper.find(route.getId(), snapshot.getEnvironment().name());
        if (active != null && releaseId.equals(active.getReleaseSnapshotId())) {
            throw new BizException(40945, "OPENAPI_ACTIVE_RELEASE_CANNOT_BE_ARCHIVED");
        }
        if (snapshot.getLifecycleState() != VersionLifecycleState.DEPRECATED) {
            throw new BizException(40945, "OPENAPI_RELEASE_MUST_BE_DEPRECATED_FIRST");
        }
        snapshot.setLifecycleState(VersionLifecycleState.ARCHIVED);
        releaseSnapshotMapper.updateById(snapshot);
        auditService.success("RELEASE_ARCHIVED", "RELEASE", releaseId, objectMapper.createObjectNode());
        return response(snapshot);
    }

    public List<ReleaseSnapshotResponse> history(String routeId, Environment environment) {
        requireRoute(routeId);
        return releaseSnapshotMapper.selectList(new LambdaQueryWrapper<ReleaseSnapshot>()
                        .eq(ReleaseSnapshot::getRouteDefinitionId, routeId)
                        .eq(ReleaseSnapshot::getEnvironment, environment)
                        .orderByDesc(ReleaseSnapshot::getReleaseNumber))
                .stream().map(this::response).toList();
    }

    public CompiledRouteRelease resolveActive(String routeKey, Environment environment) {
        String tenantId = SecurityContextHolder.getTenantId();
        return resolveActive(tenantId, routeKey, environment);
    }

    public CompiledRouteRelease resolveActive(String tenantId, String routeKey, Environment environment) {
        return cache.get(tenantId, environment, routeKey).orElseGet(() -> {
            RouteDefinition route = routeDefinitionMapper.selectOne(new LambdaQueryWrapper<RouteDefinition>()
                    .eq(RouteDefinition::getRouteKey, routeKey)
                    .eq(tenantId != null, RouteDefinition::getTenantId, tenantId)
                    .isNull(tenantId == null, RouteDefinition::getTenantId));
            if (route == null) {
                throw new BizException(40440, "OPENAPI_ROUTE_NOT_FOUND");
            }
            ActiveRelease active = activeReleaseMapper.find(route.getId(), environment.name());
            if (active == null) {
                throw new BizException(40445, "OPENAPI_ACTIVE_RELEASE_NOT_FOUND");
            }
            ReleaseSnapshot snapshot = requireRelease(active.getReleaseSnapshotId());
            CompiledRouteRelease compiled = compiled(route, snapshot, active.getPolicyVersion());
            cache.put(compiled);
            return compiled;
        });
    }

    private ObjectNode buildPinnedDependencies(RouteVersion route) {
        ObjectNode pinned = objectMapper.createObjectNode();
        RoutePlanCompiler.CompiledRoutePlan routePlan = routePlanCompiler.compile(route);
        pinned.set("routePlan", routePlan.plan());
        pinned.put("routePlanHash", routePlan.hash());
        if (StringUtils.hasText(route.getConnectorVersionId())) {
            ConnectorVersion connector = connectorVersionMapper.selectById(route.getConnectorVersionId());
            requirePublished(connector == null ? null : connector.getLifecycleState(),
                    "OPENAPI_RELEASE_CONNECTOR_NOT_PUBLISHED");
            pinned.put("connectorVersionId", connector.getId());
            pinned.put("authenticationType", connector.getAuthenticationType().name());
            if (connector.getAuthenticationType() != AuthenticationType.NONE) {
                if (!StringUtils.hasText(connector.getSecretReference())) {
                    throw new BizException(40945, "OPENAPI_RELEASE_SECRET_REFERENCE_MISSING");
                }
                pinned.put("secretReference", connector.getSecretReference());
            }
        }
        pinMapping(pinned, "requestMapping", route.getRequestMappingVersionId());
        pinMapping(pinned, "responseMapping", route.getResponseMappingVersionId());
        if (StringUtils.hasText(route.getOrchestrationVersionId())) {
            OrchestrationVersion orchestration = orchestrationVersionMapper.selectById(route.getOrchestrationVersionId());
            requirePublished(orchestration == null ? null : orchestration.getLifecycleState(),
                    "OPENAPI_RELEASE_ORCHESTRATION_NOT_PUBLISHED");
            pinned.put("orchestrationVersionId", orchestration.getId());
            pinned.put("orchestrationHash", orchestration.getDefinitionHash());
        }
        return pinned;
    }

    private void pinMapping(ObjectNode pinned, String field, String mappingVersionId) {
        if (!StringUtils.hasText(mappingVersionId)) {
            return;
        }
        MappingVersion mapping = mappingVersionMapper.selectById(mappingVersionId);
        requirePublished(mapping == null ? null : mapping.getLifecycleState(),
                "OPENAPI_RELEASE_MAPPING_NOT_PUBLISHED");
        if (mapping.getCompiledPlan() == null || !StringUtils.hasText(mapping.getCompiledPlanHash())) {
            throw new BizException(40945, "OPENAPI_RELEASE_MAPPING_PLAN_MISSING");
        }
        ObjectNode mappingPin = pinned.putObject(field);
        mappingPin.put("mappingVersionId", mapping.getId());
        mappingPin.put("sourceStructureVersionId", mapping.getSourceStructureVersionId());
        mappingPin.put("targetStructureVersionId", mapping.getTargetStructureVersionId());
        mappingPin.put("compiledPlanHash", mapping.getCompiledPlanHash());
        ArrayNode valueMaps = mappingPin.putArray("valueMapVersionIds");
        List<MappingRule> rules = mappingRuleMapper.selectList(new LambdaQueryWrapper<MappingRule>()
                .eq(MappingRule::getMappingVersionId, mappingVersionId));
        rules.stream().map(MappingRule::getOperationConfig).filter(java.util.Objects::nonNull)
                .map(config -> config.path("valueMapVersionId").asText())
                .filter(StringUtils::hasText).distinct().sorted().forEach(valueMapId -> {
                    ValueMapVersion valueMap = valueMapVersionMapper.selectById(valueMapId);
                    requirePublished(valueMap == null ? null : valueMap.getLifecycleState(),
                            "OPENAPI_RELEASE_VALUE_MAP_NOT_PUBLISHED");
                    valueMaps.add(valueMapId);
                });
    }

    private void requirePublished(VersionLifecycleState state, String message) {
        if (state != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40945, message);
        }
    }

    private RouteVersion requirePublishedRouteVersion(String id) {
        RouteVersion route = routeVersionMapper.selectById(id);
        if (route == null || route.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40945, "OPENAPI_RELEASE_ROUTE_NOT_PUBLISHED");
        }
        return route;
    }

    private RouteDefinition requireRoute(String id) {
        RouteDefinition route = routeDefinitionMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (route == null || (tenantId != null && !tenantId.equals(route.getTenantId()))) {
            throw new BizException(40440, "OPENAPI_ROUTE_NOT_FOUND");
        }
        return route;
    }

    private ReleaseSnapshot requireRelease(String id) {
        ReleaseSnapshot snapshot = releaseSnapshotMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (snapshot == null || (tenantId != null && !tenantId.equals(snapshot.getTenantId()))) {
            throw new BizException(40445, "OPENAPI_RELEASE_NOT_FOUND");
        }
        return snapshot;
    }

    private CompiledRouteRelease compiled(RouteDefinition route, ReleaseSnapshot snapshot, long policyVersion) {
        return new CompiledRouteRelease(route.getTenantId(), snapshot.getEnvironment(), route.getRouteKey(),
                route.getId(), snapshot.getRouteVersionId(), snapshot.getId(), policyVersion,
                snapshot.getSnapshotHash(), snapshot.getPinnedDependencies());
    }

    private String hash(JsonNode content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash release snapshot", exception);
        }
    }

    private ReleaseSnapshotResponse response(ReleaseSnapshot snapshot) {
        return new ReleaseSnapshotResponse(snapshot.getId(), snapshot.getTenantId(), snapshot.getEnvironment(),
                snapshot.getRouteDefinitionId(), snapshot.getRouteVersionId(), snapshot.getReleaseNumber(),
                snapshot.getLifecycleState(), snapshot.getPinnedDependencies(), snapshot.getSnapshotHash(),
                snapshot.getValidationResult(), snapshot.getReleaseNotes(), snapshot.getPublishedBy(),
                snapshot.getPublishedAt());
    }

    private String operator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
