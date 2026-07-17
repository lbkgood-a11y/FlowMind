package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.result.PageResult;
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

@Service
@RequiredArgsConstructor
public class OpenApiLifecycleCatalogService {
    private final OpenApiStructureMapper structureMapper;
    private final MappingSetMapper mappingMapper;
    private final ValueMapSetMapper valueMapMapper;
    private final ConnectorEndpointMapper connectorMapper;
    private final RouteDefinitionMapper routeMapper;
    private final ReleaseSnapshotMapper releaseMapper;
    private final OrchestrationDefinitionMapper orchestrationMapper;
    private final CallbackProfileMapper callbackMapper;
    private final ApiProductMapper productMapper;
    private final IntegrationApplicationMapper applicationMapper;
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
            case MAPPINGS -> query(type, mappingMapper, keyword, state, page, size);
            case VALUE_MAPS -> query(type, valueMapMapper, keyword, state, page, size);
            case CONNECTORS -> query(type, connectorMapper, keyword, state, page, size);
            case ROUTES -> query(type, routeMapper, keyword, state, page, size);
            case RELEASES -> query(type, releaseMapper, keyword, state, page, size);
            case ORCHESTRATIONS -> query(type, orchestrationMapper, keyword, state, page, size);
            case CALLBACKS -> query(type, callbackMapper, keyword, state, page, size);
            case PRODUCTS -> query(type, productMapper, keyword, state, page, size);
            case APPLICATIONS -> query(type, applicationMapper, keyword, state, page, size);
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
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
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

    private LifecycleAssetItem toItem(LifecycleAssetType type, Object value) {
        ObjectNode detail = objectMapper.valueToTree(value);
        redact(detail);
        return new LifecycleAssetItem(text(detail, "id"), type.path(),
                first(detail, "structureKey", "mappingKey", "valueMapKey", "connectorKey", "routeKey",
                        "releaseKey", "orchestrationKey", "callbackKey", "productKey", "applicationKey", "assetId", "id"),
                first(detail, "displayName", "assetType", "scopeType", "environment", "id"),
                first(detail, "lifecycleState", "publicationState", "decision"), text(detail, "tenantId"),
                text(detail, "createdAt"), text(detail, "updatedAt"), detail);
    }

    private void redact(JsonNode node) {
        if (!(node instanceof ObjectNode object)) return;
        List.of("secretReference", "approvalEvidence", "signature", "policyContent", "credential", "token", "password")
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
