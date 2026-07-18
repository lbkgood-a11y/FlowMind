package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.dto.CreateFormDefinitionRequest;
import com.triobase.service.lowcode.dto.FormDataResourceResponse;
import com.triobase.service.lowcode.dto.FormDefinitionResponse;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.dto.UpdateFormDefinitionRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormFieldDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
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
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String GLOBAL_TENANT_ID = "GLOBAL";

    private final FormDefinitionMapper formDefinitionMapper;
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;
    private final LowcodeFormSchemaValidator formSchemaValidator;

    @Transactional
    public FormDefinitionResponse create(CreateFormDefinitionRequest request, String operator) {
        if (!StringUtils.hasText(request.getFormKey()) || !StringUtils.hasText(request.getName())) {
            throw new BizException(40001, "FORM_KEY_OR_NAME_REQUIRED");
        }
        formSchemaValidator.validate(request.getSchemaJson(), request.getUiSchemaJson(), request.getFields());
        String tenantId = currentTenantId();
        long exists = formDefinitionMapper.selectCount(new LambdaQueryWrapper<LcFormDefinition>()
                .eq(LcFormDefinition::getTenantId, tenantId)
                .eq(LcFormDefinition::getFormKey, request.getFormKey()));
        if (exists > 0) {
            throw new BizException(40002, "FORM_KEY_ALREADY_EXISTS");
        }

        LocalDateTime now = LocalDateTime.now();
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId(UlidGenerator.nextUlid());
        definition.setTenantId(tenantId);
        definition.setFormKey(request.getFormKey());
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());
        definition.setVersion(1);
        definition.setStatus(STATUS_DRAFT);
        definition.setSchemaHash(schemaHash(request.getSchemaJson(), request.getUiSchemaJson()));
        definition.setSchemaJson(request.getSchemaJson());
        definition.setUiSchemaJson(request.getUiSchemaJson());
        definition.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        definition.setCreatedAt(now);
        definition.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        definition.setUpdatedAt(now);
        formDefinitionMapper.insert(definition);

        if (request.getFields() != null) {
            for (FormFieldSchemaRequest field : request.getFields()) {
                LcFormFieldDefinition fieldDefinition = new LcFormFieldDefinition();
                fieldDefinition.setId(UlidGenerator.nextUlid());
                fieldDefinition.setTenantId(tenantId);
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
                                .in(LcFormDefinition::getTenantId, visibleTenantIds())
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
                        .in(LcFormDefinition::getTenantId, visibleTenantIds())
                        .eq(LcFormDefinition::getStatus, STATUS_PUBLISHED)
                        .orderByAsc(LcFormDefinition::getName))
                .stream()
                .map(this::toDataResource)
                .toList();
    }

    public FormDefinitionResponse getById(String id) {
        LcFormDefinition definition = findVisibleById(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        FormDefinitionResponse response = toResponseWithoutFields(definition);
        response.setFields(formFieldDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormFieldDefinition>()
                        .eq(LcFormFieldDefinition::getFormDefinitionId, id)
                        .eq(LcFormFieldDefinition::getTenantId, definition.getTenantId())
                        .orderByAsc(LcFormFieldDefinition::getSortOrder))
                .stream()
                .map(this::toFieldRequest)
                .collect(Collectors.toList()));
        return response;
    }

    public List<FormDefinitionResponse> listVersions(String formKey) {
        return formDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormDefinition>()
                        .eq(LcFormDefinition::getFormKey, formKey)
                        .in(LcFormDefinition::getTenantId, visibleTenantIds())
                        .orderByDesc(LcFormDefinition::getVersion))
                .stream()
                .map(this::toResponseWithoutFields)
                .toList();
    }

    @Transactional
    public FormDefinitionResponse update(String id, UpdateFormDefinitionRequest request, String operator) {
        LcFormDefinition definition = requireMutableDefinition(id);
        if (!STATUS_DRAFT.equals(definition.getStatus())) {
            throw new BizException(40000, "ONLY_DRAFT_CAN_BE_MODIFIED");
        }
        if (request == null) {
            throw new BizException(40000, "FORM_DEFINITION_UPDATE_REQUIRED");
        }
        String nextName = StringUtils.hasText(request.getName()) ? request.getName().trim() : definition.getName();
        String nextDescription = request.getDescription() != null ? request.getDescription() : definition.getDescription();
        String nextSchemaJson = StringUtils.hasText(request.getSchemaJson())
                ? request.getSchemaJson() : definition.getSchemaJson();
        String nextUiSchemaJson = request.getUiSchemaJson() != null
                ? request.getUiSchemaJson() : definition.getUiSchemaJson();
        List<FormFieldSchemaRequest> nextFields = request.getFields() != null
                ? request.getFields()
                : formFieldDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormFieldDefinition>()
                        .eq(LcFormFieldDefinition::getFormDefinitionId, id)
                        .eq(LcFormFieldDefinition::getTenantId, definition.getTenantId()))
                .stream()
                .map(this::toFieldRequest)
                .toList();

        if (!StringUtils.hasText(nextName)) {
            throw new BizException(40001, "FORM_KEY_OR_NAME_REQUIRED");
        }
        formSchemaValidator.validate(nextSchemaJson, nextUiSchemaJson, nextFields);

        LocalDateTime now = LocalDateTime.now();
        definition.setName(nextName);
        definition.setDescription(nextDescription);
        definition.setSchemaJson(nextSchemaJson);
        definition.setUiSchemaJson(nextUiSchemaJson);
        definition.setSchemaHash(schemaHash(nextSchemaJson, nextUiSchemaJson));
        definition.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        definition.setUpdatedAt(now);
        formDefinitionMapper.updateById(definition);

        if (request.getFields() != null) {
            formFieldDefinitionMapper.delete(new LambdaQueryWrapper<LcFormFieldDefinition>()
                    .eq(LcFormFieldDefinition::getFormDefinitionId, id)
                    .eq(LcFormFieldDefinition::getTenantId, definition.getTenantId()));
            insertFieldDefinitions(definition.getId(), definition.getTenantId(), request.getFields(), now);
        }
        return getById(id);
    }

    @Transactional
    public FormDefinitionResponse deriveNewVersion(String sourceId, String operator) {
        LcFormDefinition source = findVisibleById(sourceId);
        if (source == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        if (STATUS_DRAFT.equals(source.getStatus())) {
            throw new BizException(40000, "DRAFT_CANNOT_CREATE_NEW_VERSION");
        }
        String targetTenantId = currentTenantId();
        ensureNoDraft(targetTenantId, source.getFormKey());
        int nextVersion = nextVersion(targetTenantId, source.getFormKey(), source.getVersion());
        LocalDateTime now = LocalDateTime.now();

        LcFormDefinition draft = new LcFormDefinition();
        draft.setId(UlidGenerator.nextUlid());
        draft.setTenantId(targetTenantId);
        draft.setFormKey(source.getFormKey());
        draft.setName(source.getName());
        draft.setDescription(source.getDescription());
        draft.setVersion(nextVersion);
        draft.setStatus(STATUS_DRAFT);
        draft.setSchemaHash(source.getSchemaHash());
        draft.setSchemaJson(source.getSchemaJson());
        draft.setUiSchemaJson(source.getUiSchemaJson());
        draft.setSourceFormDefinitionId(source.getId());
        draft.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        draft.setCreatedAt(now);
        draft.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        draft.setUpdatedAt(now);
        formDefinitionMapper.insert(draft);

        List<FormFieldSchemaRequest> sourceFields = formFieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<LcFormFieldDefinition>()
                                .eq(LcFormFieldDefinition::getFormDefinitionId, source.getId())
                                .eq(LcFormFieldDefinition::getTenantId, source.getTenantId())
                                .orderByAsc(LcFormFieldDefinition::getSortOrder))
                .stream()
                .map(this::toFieldRequest)
                .toList();
        insertFieldDefinitions(draft.getId(), targetTenantId, sourceFields, now);
        return getById(draft.getId());
    }

    @Transactional
    public FormDefinitionResponse publish(String id) {
        LcFormDefinition definition = requireMutableDefinition(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        if (!STATUS_DRAFT.equals(definition.getStatus())) {
            throw new BizException(40000, "ONLY_DRAFT_CAN_BE_PUBLISHED");
        }
        List<FormFieldSchemaRequest> fields = formFieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<LcFormFieldDefinition>()
                                .eq(LcFormFieldDefinition::getFormDefinitionId, id)
                                .eq(LcFormFieldDefinition::getTenantId, definition.getTenantId()))
                .stream()
                .map(this::toFieldRequest)
                .toList();
        formSchemaValidator.validate(definition.getSchemaJson(), definition.getUiSchemaJson(), fields);
        LocalDateTime now = LocalDateTime.now();
        definition.setStatus(STATUS_PUBLISHED);
        definition.setSchemaHash(schemaHash(definition.getSchemaJson(), definition.getUiSchemaJson()));
        definition.setPublishedAt(now);
        definition.setUpdatedAt(now);
        formDefinitionMapper.updateById(definition);
        return getById(id);
    }

    @Transactional
    public FormDefinitionResponse offline(String id) {
        LcFormDefinition definition = requireMutableDefinition(id);
        if (!STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw new BizException(40000, "ONLY_PUBLISHED_CAN_BE_OFFLINE");
        }
        LocalDateTime now = LocalDateTime.now();
        definition.setStatus(STATUS_OFFLINE);
        definition.setOfflineAt(now);
        definition.setUpdatedAt(now);
        formDefinitionMapper.updateById(definition);
        return getById(id);
    }

    public LcFormDefinition findLatestByFormKey(String formKey) {
        return formDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormDefinition>()
                        .in(LcFormDefinition::getTenantId, visibleTenantIds())
                        .eq(LcFormDefinition::getFormKey, formKey)
                        .eq(LcFormDefinition::getStatus, STATUS_PUBLISHED)
                        .orderByDesc(LcFormDefinition::getVersion))
                .stream()
                .sorted(Comparator.comparing((LcFormDefinition item) -> currentTenantId().equals(item.getTenantId()) ? 0 : 1)
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
        response.setTenantId(definition.getTenantId());
        response.setFormKey(definition.getFormKey());
        response.setVersion(definition.getVersion());
        response.setSchemaHash(definition.getSchemaHash());
        response.setSchemaJson(definition.getSchemaJson());
        response.setUiSchemaJson(definition.getUiSchemaJson());
        response.setPublishedAt(definition.getPublishedAt());
        return response;
    }

    private FormDefinitionResponse toResponseWithoutFields(LcFormDefinition definition) {
        FormDefinitionResponse response = new FormDefinitionResponse();
        response.setId(definition.getId());
        response.setTenantId(definition.getTenantId());
        response.setFormKey(definition.getFormKey());
        response.setName(definition.getName());
        response.setDescription(definition.getDescription());
        response.setVersion(definition.getVersion());
        response.setStatus(definition.getStatus());
        response.setSchemaHash(definition.getSchemaHash());
        response.setSchemaJson(definition.getSchemaJson());
        response.setUiSchemaJson(definition.getUiSchemaJson());
        response.setPublishedAt(definition.getPublishedAt());
        response.setOfflineAt(definition.getOfflineAt());
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

    private LcFormDefinition findVisibleById(String id) {
        return formDefinitionMapper.selectOne(new LambdaQueryWrapper<LcFormDefinition>()
                .eq(LcFormDefinition::getId, id)
                .in(LcFormDefinition::getTenantId, visibleTenantIds()));
    }

    private LcFormDefinition requireMutableDefinition(String id) {
        LcFormDefinition definition = findVisibleById(id);
        if (definition == null) {
            throw new BizException(40401, "FORM_DEFINITION_NOT_FOUND");
        }
        if (!canMutate(definition)) {
            throw new BizException(40301, "FORM_DEFINITION_MUTATION_DENIED");
        }
        return definition;
    }

    private boolean canMutate(LcFormDefinition definition) {
        String tenantId = currentTenantId();
        if (tenantId.equals(definition.getTenantId())) {
            return true;
        }
        return GLOBAL_TENANT_ID.equals(definition.getTenantId())
                && SecurityContextHolder.getRoles().stream().anyMatch("ADMIN"::equals);
    }

    private void ensureNoDraft(String tenantId, String formKey) {
        long draftCount = formDefinitionMapper.selectCount(new LambdaQueryWrapper<LcFormDefinition>()
                .eq(LcFormDefinition::getTenantId, tenantId)
                .eq(LcFormDefinition::getFormKey, formKey)
                .eq(LcFormDefinition::getStatus, STATUS_DRAFT));
        if (draftCount > 0) {
            throw new BizException(40900, "FORM_DRAFT_ALREADY_EXISTS");
        }
    }

    private int nextVersion(String tenantId, String formKey, Integer sourceVersion) {
        int maxVersion = formDefinitionMapper.selectList(new LambdaQueryWrapper<LcFormDefinition>()
                        .eq(LcFormDefinition::getTenantId, tenantId)
                        .eq(LcFormDefinition::getFormKey, formKey))
                .stream()
                .map(LcFormDefinition::getVersion)
                .filter(version -> version != null)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(maxVersion, sourceVersion != null ? sourceVersion : 0) + 1;
    }

    private void insertFieldDefinitions(String formDefinitionId,
                                        String tenantId,
                                        List<FormFieldSchemaRequest> fields,
                                        LocalDateTime now) {
        if (fields == null) {
            return;
        }
        for (FormFieldSchemaRequest field : fields) {
            LcFormFieldDefinition fieldDefinition = new LcFormFieldDefinition();
            fieldDefinition.setId(UlidGenerator.nextUlid());
            fieldDefinition.setTenantId(tenantId);
            fieldDefinition.setFormDefinitionId(formDefinitionId);
            fieldDefinition.setFieldKey(field.getFieldKey());
            fieldDefinition.setLabel(field.getLabel());
            fieldDefinition.setFieldType(field.getFieldType());
            fieldDefinition.setRequiredFlag(Boolean.TRUE.equals(field.getRequired()) ? 1 : 0);
            fieldDefinition.setDefaultValue(field.getDefaultValue());
            fieldDefinition.setPlaceholder(field.getPlaceholder());
            fieldDefinition.setOptionsJson(field.getOptionsJson());
            fieldDefinition.setSortOrder(field.getSortOrder() != null ? field.getSortOrder() : 0);
            fieldDefinition.setCreatedAt(now);
            fieldDefinition.setUpdatedAt(now);
            formFieldDefinitionMapper.insert(fieldDefinition);
        }
    }

    private List<String> visibleTenantIds() {
        String tenantId = currentTenantId();
        if (GLOBAL_TENANT_ID.equals(tenantId)) {
            return List.of(GLOBAL_TENANT_ID);
        }
        return List.of(tenantId, GLOBAL_TENANT_ID);
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
    }

    private String schemaHash(String schemaJson, String uiSchemaJson) {
        String payload = (schemaJson != null ? schemaJson : "") + ":" + (uiSchemaJson != null ? uiSchemaJson : "");
        return DigestUtils.md5DigestAsHex(payload.getBytes(StandardCharsets.UTF_8));
    }

}
