package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.FormRelationRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormFieldDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FormRelationGraphValidator {

    private static final int MAX_DEPTH = 3;
    private static final Set<String> CARDINALITIES = Set.of("ONE", "MANY");

    private final FormDefinitionMapper formDefinitionMapper;
    private final FormFieldDefinitionMapper fieldMapper;

    public void validate(String tenantId, String rootFormId, List<FormRelationRequest> relations) {
        if (relations == null || relations.isEmpty()) {
            return;
        }
        Set<String> codes = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (FormRelationRequest relation : relations) {
            validateRequired(relation);
            String code = relation.getRelationCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new BizException(40056, "FORM_RELATION_CODE_DUPLICATE");
            }
            String parent = relation.getParentFormDefinitionId();
            String child = relation.getChildFormDefinitionId();
            if (parent.equals(child)) {
                throw new BizException(40056, "FORM_RELATION_SELF_REFERENCE");
            }
            if (!pairs.add(parent + ":" + child)) {
                throw new BizException(40056, "FORM_RELATION_PAIR_DUPLICATE");
            }
            String cardinality = normalize(relation.getCardinality(), "MANY");
            if (!CARDINALITIES.contains(cardinality)) {
                throw new BizException(40056, "FORM_RELATION_CARDINALITY_INVALID");
            }
            LcFormDefinition parentForm = requirePublished(tenantId, parent);
            LcFormDefinition childForm = requirePublished(tenantId, child);
            requireField(parentForm, relation.getParentKeyField(), true);
            requireField(childForm, relation.getChildForeignKeyField(), false);
            graph.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(child);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        walk(rootFormId, graph, visiting, visited, 1);
        Set<String> referenced = new HashSet<>();
        graph.forEach((parent, children) -> {
            referenced.add(parent);
            referenced.addAll(children);
        });
        if (!visited.containsAll(referenced)) {
            throw new BizException(40056, "FORM_RELATION_GRAPH_DISCONNECTED");
        }
    }

    private void walk(String node, Map<String, List<String>> graph, Set<String> visiting,
                      Set<String> visited, int depth) {
        if (depth > MAX_DEPTH) {
            throw new BizException(40056, "FORM_RELATION_DEPTH_EXCEEDED");
        }
        if (!visiting.add(node)) {
            throw new BizException(40056, "FORM_RELATION_CYCLE");
        }
        for (String child : graph.getOrDefault(node, List.of())) {
            walk(child, graph, visiting, visited, depth + 1);
        }
        visiting.remove(node);
        visited.add(node);
    }

    private void validateRequired(FormRelationRequest relation) {
        if (relation == null || !StringUtils.hasText(relation.getRelationCode())
                || !StringUtils.hasText(relation.getParentFormDefinitionId())
                || !StringUtils.hasText(relation.getChildFormDefinitionId())
                || !StringUtils.hasText(relation.getChildForeignKeyField())) {
            throw new BizException(40056, "FORM_RELATION_REQUIRED");
        }
    }

    private LcFormDefinition requirePublished(String tenantId, String id) {
        LcFormDefinition form = formDefinitionMapper.selectById(id);
        if (form == null || !(tenantId.equals(form.getTenantId()) || "GLOBAL".equals(form.getTenantId()))) {
            throw new BizException(40056, "FORM_RELATION_CROSS_TENANT_OR_MISSING");
        }
        if (!"PUBLISHED".equals(form.getStatus())) {
            throw new BizException(40056, "FORM_RELATION_FORM_NOT_PUBLISHED");
        }
        return form;
    }

    private void requireField(LcFormDefinition form, String fieldKey, boolean allowSyntheticId) {
        String normalized = StringUtils.hasText(fieldKey) ? fieldKey.trim() : "id";
        if (allowSyntheticId && "id".equals(normalized)) {
            return;
        }
        long count = fieldMapper.selectCount(new LambdaQueryWrapper<LcFormFieldDefinition>()
                .eq(LcFormFieldDefinition::getFormDefinitionId, form.getId())
                .eq(LcFormFieldDefinition::getFieldKey, normalized));
        if (count == 0) {
            throw new BizException(40056, "FORM_RELATION_FIELD_NOT_FOUND");
        }
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    }
}
