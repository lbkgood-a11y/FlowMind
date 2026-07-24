package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.dto.FormInstanceGraphResponse;
import com.triobase.service.lowcode.dto.NestedFormInstanceRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.entity.LcFormInstanceRelation;
import com.triobase.service.lowcode.entity.LcFormRelation;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import com.triobase.service.lowcode.mapper.FormInstanceRelationMapper;
import com.triobase.service.lowcode.mapper.FormRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApplicationInstanceGraphService {

    private static final int MAX_NODES = 500;
    private final ApplicationVersionMapper versionMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final FormRelationMapper relationMapper;
    private final FormInstanceMapper instanceMapper;
    private final FormInstanceRelationMapper instanceRelationMapper;
    private final LowcodeFormDataValidator dataValidator;
    private final LowcodeAuthorizationService authorizationService;
    private final FormInstanceService formInstanceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public FormInstanceGraphResponse submit(String applicationVersionId, NestedFormInstanceRequest request) {
        LcApplicationVersion version = requireVersion(applicationVersionId);
        if (request == null || !version.getPrimaryFormDefinitionId().equals(request.getFormDefinitionId())) {
            throw new BizException(40057, "FORM_GRAPH_ROOT_INVALID");
        }
        List<LcFormRelation> relations = relations(applicationVersionId);
        Map<String, LcFormRelation> byParentAndCode = new HashMap<>();
        relations.forEach(relation -> byParentAndCode.put(
                relation.getParentFormDefinitionId() + ":" + relation.getRelationCode(), relation));
        int[] nodeCount = {0};
        return persistNode(version, request, byParentAndCode, null, null, 0, nodeCount);
    }

    public FormInstanceGraphResponse get(String applicationVersionId, String rootInstanceId) {
        requireVersion(applicationVersionId);
        return readNode(applicationVersionId, rootInstanceId, 1);
    }

    private FormInstanceGraphResponse persistNode(LcApplicationVersion version,
                                                  NestedFormInstanceRequest request,
                                                  Map<String, LcFormRelation> relations,
                                                  String parentInstanceId,
                                                  LcFormRelation parentRelation,
                                                  int order,
                                                  int[] nodeCount) {
        if (++nodeCount[0] > MAX_NODES) {
            throw new BizException(40057, "FORM_GRAPH_NODE_LIMIT_EXCEEDED");
        }
        LcFormDefinition definition = formDefinitionMapper.selectById(request.getFormDefinitionId());
        if (definition == null || !"PUBLISHED".equals(definition.getStatus())) {
            throw new BizException(40057, "FORM_GRAPH_FORM_NOT_PUBLISHED");
        }
        Map<String, Object> data = new LinkedHashMap<>(request.getData() != null ? request.getData() : Map.of());
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                definition.getFormKey(), "CREATE", null, data.keySet());
        if (!authorizationService.allowsCreate(decision)) {
            throw new BizException(40321, "FORM_GRAPH_CREATE_DENIED");
        }
        authorizationService.requireWritableFields(decision, data);
        if (parentRelation != null) {
            data.put(parentRelation.getChildForeignKeyField(), parentInstanceId);
        }
        dataValidator.validate(definition.getSchemaJson(), data);
        LcFormInstance instance = new LcFormInstance();
        LocalDateTime now = LocalDateTime.now();
        String user = SecurityContextHolder.getUserId();
        instance.setId(UlidGenerator.nextUlid());
        instance.setTenantId(version.getTenantId());
        instance.setFormDefinitionId(definition.getId());
        instance.setFormDefinitionVersion(definition.getVersion());
        instance.setSchemaHash(definition.getSchemaHash());
        instance.setFormKey(definition.getFormKey());
        instance.setStatus("SUBMITTED");
        instance.setDataJson(writeJson(data));
        instance.setSubmittedBy(user);
        instance.setSubmittedAt(now);
        instance.setCreatedBy(user);
        instance.setCreatedAt(now);
        instance.setUpdatedBy(user);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);
        if (parentRelation != null) {
            LcFormInstanceRelation link = new LcFormInstanceRelation();
            link.setId(UlidGenerator.nextUlid());
            link.setTenantId(version.getTenantId());
            link.setApplicationVersionId(version.getId());
            link.setRelationCode(parentRelation.getRelationCode());
            link.setParentInstanceId(parentInstanceId);
            link.setChildInstanceId(instance.getId());
            link.setSortOrder(order);
            link.setCreatedBy(user);
            link.setCreatedAt(now);
            link.setUpdatedBy(user);
            link.setUpdatedAt(now);
            instanceRelationMapper.insert(link);
        }
        FormInstanceGraphResponse response = new FormInstanceGraphResponse();
        response.setInstance(formInstanceService.getById(instance.getId()));
        Map<String, List<FormInstanceGraphResponse>> children = new LinkedHashMap<>();
        if (request.getChildren() != null) {
            request.getChildren().forEach((code, nodes) -> {
                LcFormRelation relation = relations.get(definition.getId() + ":" + code.toUpperCase());
                if (relation == null) throw new BizException(40057, "FORM_GRAPH_RELATION_INVALID");
                List<NestedFormInstanceRequest> safeNodes = nodes != null ? nodes : List.of();
                if ("ONE".equals(relation.getCardinality()) && safeNodes.size() > 1) {
                    throw new BizException(40057, "FORM_GRAPH_CARDINALITY_VIOLATION");
                }
                List<FormInstanceGraphResponse> saved = new ArrayList<>();
                for (int index = 0; index < safeNodes.size(); index++) {
                    NestedFormInstanceRequest child = safeNodes.get(index);
                    if (!relation.getChildFormDefinitionId().equals(child.getFormDefinitionId())) {
                        throw new BizException(40057, "FORM_GRAPH_CHILD_FORM_INVALID");
                    }
                    saved.add(persistNode(version, child, relations, instance.getId(), relation, index, nodeCount));
                }
                children.put(code, saved);
            });
        }
        response.setChildren(children);
        return response;
    }

    private FormInstanceGraphResponse readNode(String versionId, String instanceId, int depth) {
        if (depth > 3) throw new BizException(40057, "FORM_GRAPH_DEPTH_EXCEEDED");
        FormInstanceGraphResponse response = new FormInstanceGraphResponse();
        response.setInstance(formInstanceService.getById(instanceId));
        List<LcFormInstanceRelation> links = instanceRelationMapper.selectList(
                new LambdaQueryWrapper<LcFormInstanceRelation>()
                        .eq(LcFormInstanceRelation::getApplicationVersionId, versionId)
                        .eq(LcFormInstanceRelation::getParentInstanceId, instanceId)
                        .orderByAsc(LcFormInstanceRelation::getRelationCode)
                        .orderByAsc(LcFormInstanceRelation::getSortOrder));
        Map<String, List<FormInstanceGraphResponse>> children = new LinkedHashMap<>();
        for (LcFormInstanceRelation link : links) {
            children.computeIfAbsent(link.getRelationCode(), ignored -> new ArrayList<>())
                    .add(readNode(versionId, link.getChildInstanceId(), depth + 1));
        }
        response.setChildren(children);
        return response;
    }

    private LcApplicationVersion requireVersion(String id) {
        LcApplicationVersion version = versionMapper.selectById(id);
        if (version == null || !"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(40455, "APPLICATION_RUNTIME_NOT_FOUND");
        }
        return version;
    }

    private List<LcFormRelation> relations(String versionId) {
        return relationMapper.selectList(new LambdaQueryWrapper<LcFormRelation>()
                .eq(LcFormRelation::getApplicationVersionId, versionId)
                .orderByAsc(LcFormRelation::getSortOrder));
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new BizException(40057, "FORM_GRAPH_DATA_INVALID");
        }
    }
}
