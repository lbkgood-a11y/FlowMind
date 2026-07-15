package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.dto.CreateFormDefinitionRequest;
import com.triobase.service.lowcode.dto.FormDataResourceResponse;
import com.triobase.service.lowcode.dto.FormDefinitionResponse;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormFieldDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FormDefinitionService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final FormDefinitionMapper formDefinitionMapper;
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    @Transactional
    public FormDefinitionResponse create(CreateFormDefinitionRequest request, String operator) {
        if (!StringUtils.hasText(request.getFormKey()) || !StringUtils.hasText(request.getName())) {
            throw new BizException(40001, "FORM_KEY_OR_NAME_REQUIRED");
        }
        long exists = formDefinitionMapper.selectCount(new LambdaQueryWrapper<LcFormDefinition>()
                .eq(LcFormDefinition::getFormKey, request.getFormKey()));
        if (exists > 0) {
            throw new BizException(40002, "FORM_KEY_ALREADY_EXISTS");
        }

        LocalDateTime now = LocalDateTime.now();
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId(UlidGenerator.nextUlid());
        definition.setFormKey(request.getFormKey());
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());
        definition.setVersion(1);
        definition.setStatus(STATUS_DRAFT);
        definition.setSchemaJson(request.getSchemaJson());
        definition.setUiSchemaJson(request.getUiSchemaJson());
        definition.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);
        formDefinitionMapper.insert(definition);

        if (request.getFields() != null) {
            for (FormFieldSchemaRequest field : request.getFields()) {
                LcFormFieldDefinition fieldDefinition = new LcFormFieldDefinition();
                fieldDefinition.setId(UlidGenerator.nextUlid());
                fieldDefinition.setFormDefinitionId(definition.getId());
                fieldDefinition.setFieldKey(field.getFieldKey());
                fieldDefinition.setLabel(field.getLabel());
                fieldDefinition.setFieldType(field.getFieldType());
                fieldDefinition.setRequiredFlag(Boolean.TRUE.equals(field.getRequired()) ? 1 : 0);
                fieldDefinition.setDefaultValue(field.getDefaultValue());
                fieldDefinition.setPlaceholder(field.getPlaceholder());
                fieldDefinition.setOptionsJson(field.getOptionsJson());
                fieldDefinition.setSortOrder(field.getSortOrder() != null ? field.getSortOrder() : 0);
                fieldDefinition.setCreatedAt(now);
                formFieldDefinitionMapper.insert(fieldDefinition);
            }
        }

        return getById(definition.getId());
    }

    public PageResult<FormDefinitionResponse> list(int page, int size) {
        List<FormDefinitionResponse> all = formDefinitionMapper.selectList(
                        new LambdaQueryWrapper<LcFormDefinition>()
                                .orderByDesc(LcFormDefinition::getCreatedAt))
                .stream()
                .map(this::toResponseWithoutFields)
                .collect(Collectors.toList());
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= all.size()) {
            return PageResult.empty(page, size);
        }
        int toIndex = Math.min(fromIndex + size, all.size());
        return PageResult.of(all.subList(fromIndex, toIndex), all.size(), page, size);
    }

    public List<FormDataResourceResponse> listPublishedDataResources() {
        return formDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormDefinition>()
                        .eq(LcFormDefinition::getStatus, STATUS_PUBLISHED)
                        .orderByAsc(LcFormDefinition::getName))
                .stream()
                .map(this::toDataResource)
                .toList();
    }

    public FormDefinitionResponse getById(String id) {
        LcFormDefinition definition = formDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        FormDefinitionResponse response = toResponseWithoutFields(definition);
        response.setFields(formFieldDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormFieldDefinition>()
                        .eq(LcFormFieldDefinition::getFormDefinitionId, id)
                        .orderByAsc(LcFormFieldDefinition::getSortOrder))
                .stream()
                .map(this::toFieldRequest)
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public FormDefinitionResponse publish(String id) {
        LcFormDefinition definition = formDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        definition.setStatus(STATUS_PUBLISHED);
        definition.setUpdatedAt(LocalDateTime.now());
        formDefinitionMapper.updateById(definition);
        return getById(id);
    }

    public LcFormDefinition findLatestByFormKey(String formKey) {
        return formDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormDefinition>()
                        .eq(LcFormDefinition::getFormKey, formKey)
                        .orderByDesc(LcFormDefinition::getVersion))
                .stream()
                .sorted(Comparator.comparing((LcFormDefinition item) -> STATUS_PUBLISHED.equals(item.getStatus()) ? 0 : 1)
                        .thenComparing(LcFormDefinition::getVersion, Comparator.reverseOrder()))
                .findFirst()
                .orElseThrow(() -> new BizException(40402, "FORM_KEY_NOT_FOUND"));
    }

    public PublishedFormSnapshotResponse getPublishedSnapshot(String id) {
        LcFormDefinition definition = formDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        if (!STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw new BizException(40901, "FORM_DEFINITION_NOT_PUBLISHED");
        }
        PublishedFormSnapshotResponse response = new PublishedFormSnapshotResponse();
        response.setFormDefinitionId(definition.getId());
        response.setFormKey(definition.getFormKey());
        response.setVersion(definition.getVersion());
        response.setSchemaJson(definition.getSchemaJson());
        response.setUiSchemaJson(definition.getUiSchemaJson());
        return response;
    }

    private FormDefinitionResponse toResponseWithoutFields(LcFormDefinition definition) {
        FormDefinitionResponse response = new FormDefinitionResponse();
        response.setId(definition.getId());
        response.setFormKey(definition.getFormKey());
        response.setName(definition.getName());
        response.setDescription(definition.getDescription());
        response.setVersion(definition.getVersion());
        response.setStatus(definition.getStatus());
        response.setSchemaJson(definition.getSchemaJson());
        response.setUiSchemaJson(definition.getUiSchemaJson());
        response.setCreatedBy(definition.getCreatedBy());
        response.setCreatedAt(definition.getCreatedAt());
        return response;
    }

    private FormDataResourceResponse toDataResource(LcFormDefinition definition) {
        FormDataResourceResponse response = new FormDataResourceResponse();
        response.setResourceCode("FORM:" + definition.getFormKey().trim().toUpperCase(Locale.ROOT));
        response.setResourceName(definition.getName());
        response.setResourceType("LOWCODE_FORM");
        response.setBusinessObjectId(definition.getId());
        response.setFormKey(definition.getFormKey());
        response.setVersion(definition.getVersion());
        return response;
    }

    private FormFieldSchemaRequest toFieldRequest(LcFormFieldDefinition field) {
        FormFieldSchemaRequest response = new FormFieldSchemaRequest();
        response.setFieldKey(field.getFieldKey());
        response.setLabel(field.getLabel());
        response.setFieldType(field.getFieldType());
        response.setRequired(field.getRequiredFlag() != null && field.getRequiredFlag() == 1);
        response.setDefaultValue(field.getDefaultValue());
        response.setPlaceholder(field.getPlaceholder());
        response.setOptionsJson(field.getOptionsJson());
        response.setSortOrder(field.getSortOrder());
        return response;
    }

}
