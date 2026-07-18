package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.openapi.domain.entity.*;
import com.triobase.service.openapi.domain.enums.LifecycleAssetType;
import com.triobase.service.openapi.dto.LifecycleAssetItem;
import com.triobase.service.openapi.dto.LifecycleReadinessResponse;
import com.triobase.service.openapi.infrastructure.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class OpenApiLifecycleCatalogService {
    private final OpenApiStructureMapper structureMapper;
    private final StructureVersionMapper structureVersionMapper;
    private final MappingSetMapper mappingMapper;
    private final MappingVersionMapper mappingVersionMapper;
    private final ValueMapSetMapper valueMapMapper;
    private final ValueMapVersionMapper valueMapVersionMapper;
    private final ConnectorEndpointMapper connectorMapper;
    private final ConnectorVersionMapper connectorVersionMapper;
    private final RouteDefinitionMapper routeMapper;
    private final RouteVersionMapper routeVersionMapper;
    private final ReleaseSnapshotMapper releaseMapper;
    private final OrchestrationDefinitionMapper orchestrationMapper;
    private final OrchestrationVersionMapper orchestrationVersionMapper;
    private final CallbackProfileMapper callbackMapper;
    private final CallbackProfileVersionMapper callbackVersionMapper;
    private final ApiProductMapper productMapper;
    private final ApiProductVersionMapper productVersionMapper;
    private final IntegrationApplicationMapper applicationMapper;
    private final ApplicationClientMapper applicationClientMapper;
    private final ProductSubscriptionMapper subscriptionMapper;
    private final AssetApprovalMapper approvalMapper;
    private final TrafficPolicyVersionMapper policyMapper;
    private final PolicySnapshotMapper policySnapshotMapper;
    private final ObjectMapper objectMapper;

    @Value("${triobase.openapi.runtime.enabled:false}")
    private boolean publicRuntimeEnabled;

    public PageResult<LifecycleAssetItem> search(String assetPath, String keyword, String state,
                                                  long page, long size) {
        LifecycleAssetType type = LifecycleAssetType.fromPath(assetPath);
        return switch (type) {
            case STRUCTURES -> query(type, structureMapper, keyword, state, page, size);
            case STRUCTURE_VERSIONS -> queryVersion(type, structureMapper, "structure_key", "display_name",
                    OpenApiStructure::getId, "structure_id", structureVersionMapper, keyword, state, page, size);
            case MAPPINGS -> query(type, mappingMapper, keyword, state, page, size);
            case MAPPING_VERSIONS -> queryVersion(type, mappingMapper, "mapping_key", "display_name",
                    MappingSet::getId, "mapping_set_id", mappingVersionMapper, keyword, state, page, size);
            case VALUE_MAPS -> query(type, valueMapMapper, keyword, state, page, size);
            case VALUE_MAP_VERSIONS -> queryVersion(type, valueMapMapper, "value_map_key", "display_name",
                    ValueMapSet::getId, "value_map_set_id", valueMapVersionMapper, keyword, state, page, size);
            case CONNECTORS -> query(type, connectorMapper, keyword, state, page, size);
            case CONNECTOR_VERSIONS -> queryVersion(type, connectorMapper, "connector_key", "display_name",
                    ConnectorEndpoint::getId, "connector_endpoint_id", connectorVersionMapper, keyword, state, page, size);
            case ROUTES -> query(type, routeMapper, keyword, state, page, size);
            case ROUTE_VERSIONS -> queryVersion(type, routeMapper, "route_key", "display_name",
                    RouteDefinition::getId, "route_definition_id", routeVersionMapper, keyword, state, page, size);
            case RELEASES -> query(type, releaseMapper, keyword, state, page, size);
            case ORCHESTRATIONS -> query(type, orchestrationMapper, keyword, state, page, size);
            case ORCHESTRATION_VERSIONS -> queryVersion(type, orchestrationMapper, "orchestration_key", "display_name",
                    OrchestrationDefinition::getId, "orchestration_definition_id", orchestrationVersionMapper,
                    keyword, state, page, size);
            case CALLBACKS -> query(type, callbackMapper, keyword, state, page, size);
            case CALLBACK_VERSIONS -> queryVersion(type, callbackMapper, "callback_key", "display_name",
                    CallbackProfile::getId, "callback_profile_id", callbackVersionMapper, keyword, state, page, size);
            case PRODUCTS -> query(type, productMapper, keyword, state, page, size);
            case PRODUCT_VERSIONS -> queryVersion(type, productMapper, "product_key", "display_name",
                    ApiProduct::getId, "api_product_id", productVersionMapper, keyword, state, page, size);
            case APPLICATIONS -> query(type, applicationMapper, keyword, state, page, size);
            case APPLICATION_CLIENTS -> query(type, applicationClientMapper, keyword, state, page, size);
            case SUBSCRIPTIONS -> query(type, subscriptionMapper, keyword, state, page, size);
            case APPROVALS -> query(type, approvalMapper, keyword, state, page, size);
            case POLICIES -> query(type, policyMapper, keyword, state, page, size);
            case POLICY_SNAPSHOTS -> query(type, policySnapshotMapper, keyword, state, page, size);
        };
    }

    public LifecycleReadinessResponse readiness() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("structures", structureMapper.selectCount(null));
        counts.put("mappings", mappingMapper.selectCount(null));
        counts.put("connectors", connectorMapper.selectCount(null));
        counts.put("routes", routeMapper.selectCount(null));
        counts.put("releases", releaseMapper.selectCount(null));
        counts.put("products", productMapper.selectCount(null));
        counts.put("applications", applicationMapper.selectCount(null));
        counts.put("subscriptions", subscriptionMapper.selectCount(null));
        counts.put("policies", policySnapshotMapper.selectCount(null));
        List<LifecycleReadinessResponse.ReadinessStage> stages = List.of(
                stage("design", "API 设计", counts.get("structures") > 0 && counts.get("mappings") > 0, "/openapi-operations/structures"),
                stage("implementation", "API 实现", counts.get("connectors") > 0 && counts.get("routes") > 0 && counts.get("releases") > 0, "/openapi-operations/routes"),
                stage("exposure", "API 开放", counts.get("products") > 0 && counts.get("applications") > 0 && counts.get("subscriptions") > 0, "/openapi-operations/applications"),
                stage("policy", "策略生效", counts.get("policies") > 0, "/openapi-operations/policies"));
        List<String> blockers = new ArrayList<>();
        stages.stream().filter(item -> !item.ready()).forEach(item -> blockers.add(item.title() + "尚未就绪"));
        if (!publicRuntimeEnabled) blockers.add("公共运行时仍处于关闭状态");
        return new LifecycleReadinessResponse(stages.stream().allMatch(LifecycleReadinessResponse.ReadinessStage::ready),
                publicRuntimeEnabled, counts, stages, blockers);
    }

    private LifecycleReadinessResponse.ReadinessStage stage(String key, String title, boolean ready, String route) {
        return new LifecycleReadinessResponse.ReadinessStage(key, title, ready, route);
    }

    private <T> PageResult<LifecycleAssetItem> query(LifecycleAssetType type, BaseMapper<T> mapper,
                                                      String keyword, String state, long page, long size) {
        long safePage = safePage(page);
        long safeSize = safeSize(size);
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query.like(type.keyColumn(), keyword.trim())
                    .or().like(type.nameColumn(), keyword.trim()));
        }
        if (state != null && !state.isBlank() && type.stateColumn() != null) {
            wrapper.eq(type.stateColumn(), state.trim());
        }
        wrapper.orderByDesc(type.orderColumn());
        Page<T> result = mapper.selectPage(Page.of(safePage, safeSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(value -> toItem(type, value)).toList(),
                result.getTotal(), safePage, safeSize);
    }

    private <P, V> PageResult<LifecycleAssetItem> queryVersion(LifecycleAssetType type, BaseMapper<P> parentMapper,
                                                               String parentKeyColumn, String parentNameColumn,
                                                               Function<P, String> parentIdGetter,
                                                               String versionParentColumn, BaseMapper<V> versionMapper,
                                                               String keyword, String state, long page, long size) {
        long safePage = safePage(page);
        long safeSize = safeSize(size);
        String term = keyword == null ? null : keyword.trim();
        List<P> visibleParents = parentRecords(parentMapper, parentKeyColumn, parentNameColumn, null);
        List<String> visibleParentIds = parentIds(visibleParents, parentIdGetter);
        if (visibleParentIds.isEmpty()) {
            return PageResult.of(List.of(), 0, safePage, safeSize);
        }
        Map<String, String> parentCodes = parentReferenceCodes(visibleParents, parentIdGetter,
                parentKeyColumn, parentNameColumn);
        List<String> matchingParentIds = term == null || term.isBlank()
                ? visibleParentIds
                : parentIds(parentRecords(parentMapper, parentKeyColumn, parentNameColumn, term), parentIdGetter);
        QueryWrapper<V> wrapper = new QueryWrapper<V>().in(versionParentColumn, visibleParentIds);
        if (term != null && !term.isBlank()) {
            wrapper.and(query -> {
                if (!matchingParentIds.isEmpty()) {
                    query.in(versionParentColumn, matchingParentIds).or();
                }
                query.like(type.keyColumn(), term).or().like(type.nameColumn(), term);
            });
        }
        if (state != null && !state.isBlank() && type.stateColumn() != null) {
            wrapper.eq(type.stateColumn(), state.trim());
        }
        wrapper.orderByDesc(type.orderColumn());
        Page<V> result = versionMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        return PageResult.of(result.getRecords().stream()
                        .map(value -> toItem(type, value, parentCodes, versionParentColumn))
                        .toList(),
                result.getTotal(), safePage, safeSize);
    }

    private <P> List<P> parentRecords(BaseMapper<P> parentMapper, String keyColumn,
                                      String nameColumn, String keyword) {
        QueryWrapper<P> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query.like(keyColumn, keyword)
                    .or().like(nameColumn, keyword)
                    .or().like("id", keyword));
        }
        return parentMapper.selectList(wrapper);
    }

    private <P> List<String> parentIds(List<P> parents, Function<P, String> parentIdGetter) {
        if (parents == null || parents.isEmpty()) {
            return List.of();
        }
        return parents.stream().map(parentIdGetter)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private <P> Map<String, String> parentReferenceCodes(List<P> parents, Function<P, String> parentIdGetter,
                                                         String keyColumn, String nameColumn) {
        Map<String, String> refs = new LinkedHashMap<>();
        if (parents == null || parents.isEmpty()) {
            return refs;
        }
        parents.forEach(parent -> {
            String id = parentIdGetter.apply(parent);
            if (id == null || id.isBlank()) {
                return;
            }
            ObjectNode detail = objectMapper.valueToTree(parent);
            refs.put(id, first(detail, snakeToCamel(keyColumn), snakeToCamel(nameColumn), "id"));
        });
        return refs;
    }

    private long safePage(long page) {
        return Math.max(1, page);
    }

    private long safeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }

    private LifecycleAssetItem toItem(LifecycleAssetType type, Object value) {
        return toItem(type, value, Map.of(), null);
    }

    private LifecycleAssetItem toItem(LifecycleAssetType type, Object value,
                                      Map<String, String> parentReferenceCodes, String versionParentColumn) {
        ObjectNode detail = objectMapper.valueToTree(value);
        if (versionParentColumn != null && !versionParentColumn.isBlank()) {
            String parentId = text(detail, snakeToCamel(versionParentColumn));
            String parentCode = parentReferenceCodes.get(parentId);
            if (parentCode != null && !parentCode.isBlank()) {
                detail.put("parentReferenceCode", parentCode);
            }
        }
        redact(detail);
        String rawAssetKey = first(detail, "structureKey", "mappingKey", "valueMapKey", "connectorKey", "routeKey",
                "releaseKey", "orchestrationKey", "callbackKey", "productKey", "applicationKey", "clientKey",
                "semanticVersion", "versionNumber", "assetId", "id");
        String referenceCode = referenceCode(type, detail, rawAssetKey);
        if (referenceCode != null && !referenceCode.isBlank()) {
            detail.put("referenceCode", referenceCode);
        }
        return new LifecycleAssetItem(text(detail, "id"), type.path(),
                first(detail, "referenceCode", "structureKey", "mappingKey", "valueMapKey", "connectorKey", "routeKey",
                        "releaseKey", "orchestrationKey", "callbackKey", "productKey", "applicationKey", "clientKey",
                        "semanticVersion", "versionNumber", "assetId", "id"),
                first(detail, "displayName", "referenceCode", "assetType", "scopeType", "environment", "id"),
                first(detail, "lifecycleState", "publicationState", "decision"), text(detail, "tenantId"),
                text(detail, "createdAt"), text(detail, "updatedAt"), detail);
    }

    private String referenceCode(LifecycleAssetType type, ObjectNode detail, String fallback) {
        return switch (type) {
            case STRUCTURE_VERSIONS -> versionCode(detail, "structureId", text(detail, "versionNumber"));
            case MAPPING_VERSIONS -> versionCode(detail, "mappingSetId", text(detail, "versionNumber"));
            case VALUE_MAP_VERSIONS -> versionCode(detail, "valueMapSetId", text(detail, "versionNumber"));
            case CONNECTOR_VERSIONS -> versionCode(detail, "connectorEndpointId", text(detail, "versionNumber"));
            case ROUTE_VERSIONS -> versionCode(detail, "routeDefinitionId", text(detail, "versionNumber"));
            case ORCHESTRATION_VERSIONS -> versionCode(detail, "orchestrationDefinitionId", text(detail, "versionNumber"));
            case CALLBACK_VERSIONS -> versionCode(detail, "callbackProfileId", text(detail, "versionNumber"));
            case PRODUCT_VERSIONS -> versionCode(detail, "apiProductId", text(detail, "semanticVersion"));
            case APPLICATION_CLIENTS -> first(detail, "clientKey", "applicationId", "id");
            default -> fallback;
        };
    }

    private String versionCode(ObjectNode detail, String ownerField, String version) {
        String owner = first(detail, "parentReferenceCode", ownerField);
        String versionLabel = version == null || version.isBlank() ? null
                : version.matches("\\d+") ? "v" + version : version;
        if (owner != null && !owner.isBlank() && versionLabel != null) {
            return owner + ":" + versionLabel;
        }
        return first(detail, ownerField, "id");
    }

    private String snakeToCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(character) : character);
            upperNext = false;
        }
        return builder.toString();
    }

    private void redact(JsonNode node) {
        if (!(node instanceof ObjectNode object)) return;
        List.of("secretReference", "approvalEvidence", "signature", "credential", "token", "password")
                .forEach(field -> { if (object.has(field)) object.put(field, "***REDACTED***"); });
        object.elements().forEachRemaining(this::redact);
    }

    private String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
