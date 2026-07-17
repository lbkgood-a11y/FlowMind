package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.OrchestrationVersion;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateRouteRequest;
import com.triobase.service.openapi.dto.RouteResolutionContext;
import com.triobase.service.openapi.dto.RouteVersionMutationRequest;
import com.triobase.service.openapi.dto.RouteVersionResponse;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteRegistryService {

    private final RouteDefinitionMapper definitionMapper;
    private final RouteVersionMapper versionMapper;
    private final ConnectorVersionMapper connectorVersionMapper;
    private final MappingVersionMapper mappingVersionMapper;
    private final OrchestrationVersionMapper orchestrationVersionMapper;
    private final RoutePredicateEvaluator predicateEvaluator;
    private final RoutePlanCompiler routePlanCompiler;
    private final IntegrationAuditService auditService;

    @Transactional
    public RouteVersionResponse create(CreateRouteRequest request) {
        validate(request);
        String tenantId = targetTenant(request.tenantId());
        if (definitionMapper.selectCount(new LambdaQueryWrapper<RouteDefinition>()
                .eq(RouteDefinition::getRouteKey, request.routeKey().trim())
                .eq(tenantId != null, RouteDefinition::getTenantId, tenantId)
                .isNull(tenantId == null, RouteDefinition::getTenantId)) > 0) {
            throw new BizException(40940, "OPENAPI_ROUTE_ALREADY_EXISTS");
        }
        predicateEvaluator.validate(request.routePredicate());
        RouteDefinition definition = new RouteDefinition();
        LocalDateTime now = LocalDateTime.now();
        definition.setId(UlidGenerator.nextUlid());
        definition.setTenantId(tenantId);
        definition.setRouteKey(request.routeKey().trim());
        definition.setDisplayName(request.displayName().trim());
        definition.setOwnerId(request.ownerId().trim());
        definition.setLifecycleState(AssetLifecycleState.ACTIVE);
        definition.setRowVersion(0L);
        definition.setCreatedBy(operator());
        definition.setCreatedAt(now);
        definition.setUpdatedBy(operator());
        definition.setUpdatedAt(now);
        definitionMapper.insert(definition);

        RouteVersion version = new RouteVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setRouteDefinitionId(definition.getId());
        version.setVersionNumber(1);
        version.setEnvironment(request.environment());
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request);
        initialize(version, now);
        versionMapper.insert(version);
        auditService.success("ROUTE_CREATED", "ROUTE", definition.getId(),
                JsonNodeFactory.instance.objectNode().put("versionId", version.getId()));
        return response(definition, version);
    }

    @Transactional
    public RouteVersionResponse createDraft(
            String routeId, Environment environment, RouteVersionMutationRequest request) {
        RouteDefinition definition = requireDefinition(routeId);
        validate(request);
        predicateEvaluator.validate(request.routePredicate());
        if (versionMapper.selectCount(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteDefinitionId, routeId)
                .eq(RouteVersion::getEnvironment, environment)
                .eq(RouteVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(40940, "OPENAPI_ROUTE_DRAFT_ALREADY_EXISTS");
        }
        RouteVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteDefinitionId, routeId)
                .eq(RouteVersion::getEnvironment, environment)
                .orderByDesc(RouteVersion::getVersionNumber).last("LIMIT 1"));
        RouteVersion version = new RouteVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setRouteDefinitionId(routeId);
        version.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        version.setEnvironment(environment);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request);
        initialize(version, LocalDateTime.now());
        versionMapper.insert(version);
        auditService.success("ROUTE_DRAFT_CREATED", "ROUTE_VERSION", version.getId(),
                JsonNodeFactory.instance.objectNode());
        return response(definition, version);
    }

    @Transactional
    public RouteVersionResponse updateDraft(String versionId, RouteVersionMutationRequest request) {
        validate(request);
        predicateEvaluator.validate(request.routePredicate());
        RouteVersion version = requireVersion(versionId);
        RouteDefinition definition = requireDefinition(version.getRouteDefinitionId());
        requireDraft(version);
        apply(version, request);
        touch(version);
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40941, "OPENAPI_ROUTE_VERSION_CONFLICT");
        }
        auditService.success("ROUTE_DRAFT_UPDATED", "ROUTE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return response(definition, version);
    }

    @Transactional
    public RouteVersionResponse publish(String versionId) {
        RouteVersion version = requireVersion(versionId);
        RouteDefinition definition = requireDefinition(version.getRouteDefinitionId());
        requireDraft(version);
        validateDependencies(version);
        rejectAmbiguity(version);
        routePlanCompiler.compile(version);
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(operator());
        version.setPublishedAt(LocalDateTime.now());
        touch(version);
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40941, "OPENAPI_ROUTE_VERSION_CONFLICT");
        }
        auditService.success("ROUTE_PUBLISHED", "ROUTE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return response(definition, version);
    }

    public RouteVersionResponse getVersion(String versionId) {
        RouteVersion version = requireVersion(versionId);
        return response(requireDefinition(version.getRouteDefinitionId()), version);
    }

    @Transactional
    public RouteVersionResponse deprecate(String versionId) {
        RouteVersion version = requireVersion(versionId);
        RouteDefinition definition = requireDefinition(version.getRouteDefinitionId());
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40941, "OPENAPI_ONLY_PUBLISHED_ROUTE_CAN_BE_DEPRECATED");
        }
        version.setLifecycleState(VersionLifecycleState.DEPRECATED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("ROUTE_DEPRECATED", "ROUTE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return response(definition, version);
    }

    @Transactional
    public RouteVersionResponse archiveVersion(String versionId) {
        RouteVersion version = requireVersion(versionId);
        RouteDefinition definition = requireDefinition(version.getRouteDefinitionId());
        if (version.getLifecycleState() == VersionLifecycleState.PUBLISHED) {
            throw new BizException(40941, "OPENAPI_PUBLISHED_ROUTE_MUST_BE_DEPRECATED_FIRST");
        }
        version.setLifecycleState(VersionLifecycleState.ARCHIVED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("ROUTE_VERSION_ARCHIVED", "ROUTE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return response(definition, version);
    }

    @Transactional
    public void archiveRoute(String routeId) {
        RouteDefinition definition = requireDefinition(routeId);
        if (versionMapper.selectCount(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteDefinitionId, routeId)
                .in(RouteVersion::getLifecycleState,
                        VersionLifecycleState.DRAFT, VersionLifecycleState.PUBLISHED)) > 0) {
            throw new BizException(40940, "OPENAPI_ROUTE_HAS_ACTIVE_VERSIONS");
        }
        definition.setLifecycleState(AssetLifecycleState.ARCHIVED);
        definition.setUpdatedBy(operator());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        auditService.success("ROUTE_ARCHIVED", "ROUTE", routeId, JsonNodeFactory.instance.objectNode());
    }

    public RouteVersionResponse resolve(
            String routeKey, Environment environment, RouteResolutionContext context) {
        RouteDefinition definition = findDefinition(routeKey);
        LocalDateTime effectiveAt = context.effectiveAt();
        List<RouteVersion> candidates = versionMapper.selectList(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteDefinitionId, definition.getId())
                .eq(RouteVersion::getEnvironment, environment)
                .eq(RouteVersion::getLifecycleState, VersionLifecycleState.PUBLISHED)
                .eq(RouteVersion::getEnabled, true));
        List<RouteVersion> matching = candidates.stream()
                .filter(version -> withinWindow(version, effectiveAt))
                .filter(version -> predicateEvaluator.matches(version.getRoutePredicate(), context))
                .sorted(Comparator.comparing(RouteVersion::getPriority).reversed()
                        .thenComparing(Comparator.comparing(RouteVersion::getVersionNumber).reversed()))
                .toList();
        if (matching.isEmpty()) {
            throw new BizException(40441, "OPENAPI_ROUTE_NOT_RESOLVED");
        }
        if (matching.size() > 1 && matching.get(0).getPriority().equals(matching.get(1).getPriority())) {
            throw new BizException(40942, "OPENAPI_ROUTE_RUNTIME_AMBIGUITY");
        }
        return response(definition, matching.get(0));
    }

    private void rejectAmbiguity(RouteVersion candidate) {
        List<RouteVersion> published = versionMapper.selectList(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteDefinitionId, candidate.getRouteDefinitionId())
                .eq(RouteVersion::getEnvironment, candidate.getEnvironment())
                .eq(RouteVersion::getLifecycleState, VersionLifecycleState.PUBLISHED)
                .eq(RouteVersion::getEnabled, true)
                .eq(RouteVersion::getPriority, candidate.getPriority()));
        boolean ambiguous = published.stream().anyMatch(existing -> windowsOverlap(candidate, existing)
                && predicateEvaluator.canOverlap(candidate.getRoutePredicate(), existing.getRoutePredicate()));
        if (ambiguous) {
            throw new BizException(40942, "OPENAPI_ROUTE_PUBLICATION_AMBIGUOUS");
        }
    }

    private void validateDependencies(RouteVersion version) {
        if (version.getExecutionMode() == ExecutionMode.SYNCHRONOUS) {
            requirePublished(connectorVersionMapper.selectById(version.getConnectorVersionId()),
                    "OPENAPI_ROUTE_CONNECTOR_NOT_PUBLISHED");
        } else {
            requirePublished(orchestrationVersionMapper.selectById(version.getOrchestrationVersionId()),
                    "OPENAPI_ROUTE_ORCHESTRATION_NOT_PUBLISHED");
        }
        if (StringUtils.hasText(version.getRequestMappingVersionId())) {
            requirePublished(mappingVersionMapper.selectById(version.getRequestMappingVersionId()),
                    "OPENAPI_ROUTE_REQUEST_MAPPING_NOT_PUBLISHED");
        }
        if (StringUtils.hasText(version.getResponseMappingVersionId())) {
            requirePublished(mappingVersionMapper.selectById(version.getResponseMappingVersionId()),
                    "OPENAPI_ROUTE_RESPONSE_MAPPING_NOT_PUBLISHED");
        }
    }

    private void requirePublished(Object dependency, String message) {
        VersionLifecycleState state = switch (dependency) {
            case ConnectorVersion connector -> connector.getLifecycleState();
            case MappingVersion mapping -> mapping.getLifecycleState();
            case OrchestrationVersion orchestration -> orchestration.getLifecycleState();
            case null -> null;
            default -> null;
        };
        if (state != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40943, message);
        }
    }

    private RouteDefinition findDefinition(String routeKey) {
        String tenantId = SecurityContextHolder.getTenantId();
        RouteDefinition definition = definitionMapper.selectOne(new LambdaQueryWrapper<RouteDefinition>()
                .eq(RouteDefinition::getRouteKey, routeKey)
                .eq(tenantId != null, RouteDefinition::getTenantId, tenantId)
                .isNull(tenantId == null, RouteDefinition::getTenantId)
                .eq(RouteDefinition::getLifecycleState, AssetLifecycleState.ACTIVE));
        if (definition == null) {
            throw new BizException(40440, "OPENAPI_ROUTE_NOT_FOUND");
        }
        return definition;
    }

    private RouteDefinition requireDefinition(String id) {
        RouteDefinition definition = definitionMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (definition == null || (tenantId != null && !tenantId.equals(definition.getTenantId()))) {
            throw new BizException(40440, "OPENAPI_ROUTE_NOT_FOUND");
        }
        return definition;
    }

    private RouteVersion requireVersion(String id) {
        RouteVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(40441, "OPENAPI_ROUTE_VERSION_NOT_FOUND");
        }
        requireDefinition(version.getRouteDefinitionId());
        return version;
    }

    private void requireDraft(RouteVersion version) {
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40941, "OPENAPI_PUBLISHED_ROUTE_IMMUTABLE");
        }
    }

    private void validate(CreateRouteRequest request) {
        if (request == null || !StringUtils.hasText(request.routeKey())
                || !StringUtils.hasText(request.displayName()) || !StringUtils.hasText(request.ownerId())
                || request.environment() == null || request.executionMode() == null
                || invalidWindow(request.effectiveFrom(), request.effectiveUntil())
                || invalidDependencies(request.executionMode(), request.connectorVersionId(), request.orchestrationVersionId())) {
            throw new BizException(40040, "OPENAPI_ROUTE_REQUEST_INVALID");
        }
    }

    private void validate(RouteVersionMutationRequest request) {
        if (request == null || request.executionMode() == null
                || invalidWindow(request.effectiveFrom(), request.effectiveUntil())
                || invalidDependencies(request.executionMode(), request.connectorVersionId(), request.orchestrationVersionId())) {
            throw new BizException(40040, "OPENAPI_ROUTE_REQUEST_INVALID");
        }
    }

    private boolean invalidWindow(LocalDateTime from, LocalDateTime until) {
        return from != null && until != null && !until.isAfter(from);
    }

    private boolean invalidDependencies(ExecutionMode mode, String connector, String orchestration) {
        return mode == ExecutionMode.SYNCHRONOUS
                ? !StringUtils.hasText(connector) || StringUtils.hasText(orchestration)
                : !StringUtils.hasText(orchestration);
    }

    private void apply(RouteVersion version, CreateRouteRequest request) {
        version.setPriority(request.priority());
        version.setEffectiveFrom(request.effectiveFrom());
        version.setEffectiveUntil(request.effectiveUntil());
        version.setEnabled(request.enabled());
        version.setRoutePredicate(request.routePredicate() == null
                ? JsonNodeFactory.instance.objectNode() : request.routePredicate().deepCopy());
        version.setExecutionMode(request.executionMode());
        version.setConnectorVersionId(request.connectorVersionId());
        version.setRequestMappingVersionId(request.requestMappingVersionId());
        version.setResponseMappingVersionId(request.responseMappingVersionId());
        version.setOrchestrationVersionId(request.orchestrationVersionId());
    }

    private void apply(RouteVersion version, RouteVersionMutationRequest request) {
        version.setPriority(request.priority());
        version.setEffectiveFrom(request.effectiveFrom());
        version.setEffectiveUntil(request.effectiveUntil());
        version.setEnabled(request.enabled());
        version.setRoutePredicate(request.routePredicate() == null
                ? JsonNodeFactory.instance.objectNode() : request.routePredicate().deepCopy());
        version.setExecutionMode(request.executionMode());
        version.setConnectorVersionId(request.connectorVersionId());
        version.setRequestMappingVersionId(request.requestMappingVersionId());
        version.setResponseMappingVersionId(request.responseMappingVersionId());
        version.setOrchestrationVersionId(request.orchestrationVersionId());
    }

    private void initialize(RouteVersion version, LocalDateTime now) {
        version.setRowVersion(0L);
        version.setCreatedBy(operator());
        version.setCreatedAt(now);
        version.setUpdatedBy(operator());
        version.setUpdatedAt(now);
    }

    private void touch(RouteVersion version) {
        version.setUpdatedBy(operator());
        version.setUpdatedAt(LocalDateTime.now());
    }

    private boolean withinWindow(RouteVersion version, LocalDateTime at) {
        return (version.getEffectiveFrom() == null || !at.isBefore(version.getEffectiveFrom()))
                && (version.getEffectiveUntil() == null || at.isBefore(version.getEffectiveUntil()));
    }

    private boolean windowsOverlap(RouteVersion left, RouteVersion right) {
        return (left.getEffectiveUntil() == null || right.getEffectiveFrom() == null
                || left.getEffectiveUntil().isAfter(right.getEffectiveFrom()))
                && (right.getEffectiveUntil() == null || left.getEffectiveFrom() == null
                || right.getEffectiveUntil().isAfter(left.getEffectiveFrom()));
    }

    private RouteVersionResponse response(RouteDefinition definition, RouteVersion version) {
        return new RouteVersionResponse(definition.getId(), version.getId(), definition.getTenantId(),
                definition.getRouteKey(), definition.getDisplayName(), version.getVersionNumber(),
                version.getEnvironment(), version.getLifecycleState(), version.getPriority(),
                version.getEffectiveFrom(), version.getEffectiveUntil(), version.getEnabled(),
                version.getRoutePredicate(), version.getExecutionMode(), version.getConnectorVersionId(),
                version.getRequestMappingVersionId(), version.getResponseMappingVersionId(),
                version.getOrchestrationVersionId());
    }

    private String targetTenant(String requested) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            return StringUtils.hasText(requested) ? requested.trim() : null;
        }
        if (StringUtils.hasText(requested) && !current.equals(requested.trim())) {
            throw new BizException(40310, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String operator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
