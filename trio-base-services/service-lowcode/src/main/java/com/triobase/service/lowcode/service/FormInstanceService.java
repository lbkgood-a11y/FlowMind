package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormInstanceService {

    private final FormDefinitionService formDefinitionService;
    private final FormInstanceMapper formInstanceMapper;
    private final ObjectMapper objectMapper;
    private final DataScopeProvider dataScopeProvider;

    @Transactional
    public FormInstanceResponse submit(String formKey, SubmitFormInstanceRequest request) {
        LcFormDefinition definition = formDefinitionService.findLatestByFormKey(formKey);
        String userId = requireCurrentUser();
        DataScope dataScope = dataScopeProvider.resolve(userId, resourceCode(formKey), "CREATE");
        if (dataScope == null || dataScope.restrictive()
                || (!dataScope.allowsAll() && !dataScope.allowsSelf())) {
            throw new BizException(40311, "FORM_DATA_CREATE_DENIED");
        }
        LcFormInstance instance = new LcFormInstance();
        instance.setId(UlidGenerator.nextUlid());
        instance.setFormDefinitionId(definition.getId());
        instance.setFormKey(definition.getFormKey());
        instance.setStatus("SUBMITTED");
        instance.setSubmittedBy(userId);
        instance.setSubmittedAt(LocalDateTime.now());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setDataJson(toJson(request));
        formInstanceMapper.insert(instance);
        return toResponse(instance);
    }

    public PageResult<FormInstanceResponse> list(String formKey, int page, int size) {
        String userId = requireCurrentUser();
        DataScope dataScope = dataScopeProvider.resolve(userId, resourceCode(formKey), "QUERY");
        if (dataScope == null || dataScope.restrictive()) {
            return PageResult.empty(page, size);
        }

        LambdaQueryWrapper<LcFormInstance> query = new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getFormKey, formKey)
                .orderByDesc(LcFormInstance::getSubmittedAt);
        if (!dataScope.allowsAll()) {
            if (!dataScope.allowsSelf()) {
                return PageResult.empty(page, size);
            }
            query.eq(LcFormInstance::getSubmittedBy, userId);
        }
        List<FormInstanceResponse> all = formInstanceMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= all.size()) {
            return PageResult.empty(page, size);
        }
        int toIndex = Math.min(fromIndex + size, all.size());
        return PageResult.of(all.subList(fromIndex, toIndex), all.size(), page, size);
    }

    public FormInstanceResponse getById(String id) {
        LcFormInstance instance = formInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        return toResponse(instance);
    }

    @Transactional
    public FormInstanceResponse bindProcess(String formKey, String instanceId, BindFormProcessRequest request) {
        String userId = requireCurrentUser();
        if (request == null || !StringUtils.hasText(request.getProcessInstanceId())
                || !StringUtils.hasText(request.getProcessKey())) {
            throw new BizException(40012, "FORM_PROCESS_BINDING_REQUIRED");
        }
        LcFormInstance instance = formInstanceMapper.selectById(instanceId);
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        DataScope dataScope = dataScopeProvider.resolve(userId, resourceCode(formKey), "QUERY");
        boolean canBind = dataScope != null && !dataScope.restrictive()
                && (dataScope.allowsAll()
                || (dataScope.allowsSelf() && userId.equals(instance.getSubmittedBy())));
        if (!canBind) {
            throw new BizException(40312, "FORM_PROCESS_BINDING_DENIED");
        }
        if (StringUtils.hasText(instance.getProcessInstanceId())
                && !instance.getProcessInstanceId().equals(request.getProcessInstanceId())) {
            throw new BizException(40912, "FORM_PROCESS_ALREADY_BOUND");
        }
        instance.setProcessKey(request.getProcessKey().trim());
        instance.setProcessInstanceId(request.getProcessInstanceId().trim());
        instance.setWorkflowStatus(StringUtils.hasText(request.getWorkflowStatus())
                ? request.getWorkflowStatus().trim() : "RUNNING");
        formInstanceMapper.updateById(instance);
        return toResponse(instance);
    }

    private String toJson(SubmitFormInstanceRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getData());
        } catch (JsonProcessingException e) {
            throw new BizException(40003, "FORM_INSTANCE_JSON_INVALID");
        }
    }

    private String requireCurrentUser() {
        String userId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40310, "FORM_DATA_LOGIN_REQUIRED");
        }
        return userId;
    }

    private String resourceCode(String formKey) {
        return "FORM:" + formKey.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private FormInstanceResponse toResponse(LcFormInstance instance) {
        FormInstanceResponse response = new FormInstanceResponse();
        response.setId(instance.getId());
        response.setFormDefinitionId(instance.getFormDefinitionId());
        response.setFormKey(instance.getFormKey());
        response.setStatus(instance.getStatus());
        response.setDataJson(instance.getDataJson());
        response.setSubmittedBy(instance.getSubmittedBy());
        response.setProcessKey(instance.getProcessKey());
        response.setProcessInstanceId(instance.getProcessInstanceId());
        response.setWorkflowStatus(instance.getWorkflowStatus());
        response.setSubmittedAt(instance.getSubmittedAt());
        return response;
    }

}
