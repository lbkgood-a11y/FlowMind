package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.service.lowcode.action.LowcodeActionExecutionContext;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.entity.LcFormInstanceWorkflowAudit;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import com.triobase.service.lowcode.mapper.FormInstanceWorkflowAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FormInstanceService {
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String GLOBAL_TENANT_ID = "GLOBAL";
    private static final String STATUS_PENDING_WORKFLOW = "PENDING_WORKFLOW";
    private static final Set<String> SUPPORTED_WORKFLOW_STATUSES = Set.of(
            STATUS_PENDING_WORKFLOW, "RUNNING", "COMPLETED", "FAILED", "TERMINATED", "CANCELLED");

    private final FormDefinitionService formDefinitionService;
    private final FormInstanceMapper formInstanceMapper;
    private final FormInstanceWorkflowAuditMapper workflowAuditMapper;
    private final ObjectMapper objectMapper;
    private final LowcodeFormDataValidator formDataValidator;
    private final LowcodeAuthorizationService authorizationService;

    @Transactional
    public FormInstanceResponse submit(String formKey, SubmitFormInstanceRequest request) {
        return submit(formKey, request, "CREATE");
    }

    @Transactional
    public FormInstanceResponse submit(String formKey, SubmitFormInstanceRequest request, String actionCode) {
        if (request == null) {
            throw new BizException(40003, "FORM_INSTANCE_REQUEST_REQUIRED");
        }
        LcFormDefinition definition = formDefinitionService.findLatestByFormKey(formKey);
        String userId = requireCurrentUser();
        String tenantId = currentTenantId();
        Map<String, Object> requestData = request.getData() != null ? request.getData() : Map.of();
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, actionCode, null, requestData.keySet());
        if (!authorizationService.allowsCreate(decision)) {
            throw new BizException(40311, "FORM_DATA_CREATE_DENIED");
        }
        authorizationService.requireWritableFields(decision, requestData);
        formDataValidator.validate(definition.getSchemaJson(), request.getData());
        LcFormInstance instance = new LcFormInstance();
        instance.setId(UlidGenerator.nextUlid());
        instance.setTenantId(tenantId);
        instance.setFormDefinitionId(definition.getId());
        instance.setFormDefinitionVersion(definition.getVersion());
        instance.setSchemaHash(definition.getSchemaHash());
        instance.setFormKey(definition.getFormKey());
        instance.setStatus("SUBMITTED");
        instance.setSubmittedBy(userId);
        instance.setSubmittedAt(LocalDateTime.now());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setCreatedBy(userId);
        instance.setUpdatedBy(userId);
        instance.setUpdatedAt(instance.getCreatedAt());
        instance.setDataJson(toJson(request));
        applyActionMetadata(instance);
        formInstanceMapper.insert(instance);
        return toResponse(instance);
    }

    public PageResult<FormInstanceResponse> list(String formKey, int page, int size) {
        String userId = requireCurrentUser();
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "VIEW", null, List.of());
        LowcodeAuthorizationService.DataAccessMode accessMode = authorizationService.dataAccessMode(decision);
        if (accessMode == LowcodeAuthorizationService.DataAccessMode.DENIED
                || accessMode == LowcodeAuthorizationService.DataAccessMode.ORG) {
            return PageResult.empty(page, size);
        }

        LambdaQueryWrapper<LcFormInstance> query = new LambdaQueryWrapper<LcFormInstance>()
                .in(LcFormInstance::getTenantId, visibleTenantIds())
                .eq(LcFormInstance::getFormKey, formKey)
                .orderByDesc(LcFormInstance::getSubmittedAt);
        if (accessMode == LowcodeAuthorizationService.DataAccessMode.SELF) {
            query.eq(LcFormInstance::getSubmittedBy, userId);
        }
        IPage<LcFormInstance> result = formInstanceMapper.selectPage(new Page<>(page, size), query);
        List<FormInstanceResponse> records = result.getRecords().stream()
                .map(this::toResponse)
                .map(response -> authorizationService.applyReadRules(response, decision))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public FormInstanceResponse getById(String id) {
        String userId = requireCurrentUser();
        LcFormInstance instance = findVisibleById(id);
        if (instance == null) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                instance.getFormKey(), "VIEW", instance.getId(), List.of());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40313, "FORM_INSTANCE_DETAIL_DENIED");
        }
        return authorizationService.applyReadRules(toResponse(instance), decision);
    }

    @Transactional
    public FormInstanceResponse update(String formKey, String instanceId, UpdateFormInstanceRequest request) {
        String userId = requireCurrentUser();
        if (request == null) {
            throw new BizException(40003, "FORM_INSTANCE_REQUEST_REQUIRED");
        }
        LcFormDefinition definition = formDefinitionService.findLatestByFormKey(formKey);
        LcFormInstance instance = formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, instanceId)
                .eq(LcFormInstance::getFormKey, formKey)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        Map<String, Object> requestData = request.getData() != null ? request.getData() : Map.of();
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "EDIT", instanceId, requestData.keySet());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40315, "FORM_INSTANCE_EDIT_DENIED");
        }
        authorizationService.requireWritableFields(decision, requestData);
        formDataValidator.validate(definition.getSchemaJson(), request.getData());
        LocalDateTime now = LocalDateTime.now();
        instance.setDataJson(toJsonRaw(requestData));
        instance.setUpdatedBy(userId);
        instance.setUpdatedAt(now);
        applyActionMetadata(instance);
        formInstanceMapper.updateById(instance);
        return authorizationService.applyReadRules(toResponse(instance), decision);
    }

    public FormInstanceResponse export(String formKey, String instanceId) {
        String userId = requireCurrentUser();
        LcFormInstance instance = formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, instanceId)
                .eq(LcFormInstance::getFormKey, formKey)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "EXPORT", instance.getId(), List.of());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40319, "FORM_INSTANCE_EXPORT_DENIED");
        }
        return authorizationService.applyReadRules(toResponse(instance), decision);
    }

    @Transactional
    public void delete(String formKey, String instanceId) {
        String userId = requireCurrentUser();
        LcFormInstance instance = formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, instanceId)
                .eq(LcFormInstance::getFormKey, formKey)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "DELETE", instance.getId(), List.of());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40318, "FORM_INSTANCE_DELETE_DENIED");
        }
        formInstanceMapper.deleteById(instance.getId());
    }

    @Transactional
    public FormInstanceResponse bindProcess(String formKey, String instanceId, BindFormProcessRequest request) {
        String userId = requireCurrentUser();
        if (request == null || !StringUtils.hasText(request.getProcessInstanceId())
                || !StringUtils.hasText(request.getProcessKey())) {
            throw new BizException(40012, "FORM_PROCESS_BINDING_REQUIRED");
        }
        LcFormInstance instance = formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, instanceId)
                .eq(LcFormInstance::getFormKey, formKey)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "EDIT", instance.getId(), List.of());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40312, "FORM_PROCESS_BINDING_DENIED");
        }
        if (StringUtils.hasText(instance.getProcessInstanceId())
                && !instance.getProcessInstanceId().equals(request.getProcessInstanceId())) {
            throw new BizException(40912, "FORM_PROCESS_ALREADY_BOUND");
        }
        String previousStatus = instance.getWorkflowStatus();
        String nextStatus = normalizeWorkflowStatus(
                StringUtils.hasText(request.getWorkflowStatus()) ? request.getWorkflowStatus() : "RUNNING");
        String nextProcessKey = request.getProcessKey().trim();
        String nextProcessInstanceId = request.getProcessInstanceId().trim();
        boolean unchanged = nextProcessKey.equals(instance.getProcessKey())
                && nextProcessInstanceId.equals(instance.getProcessInstanceId())
                && nextStatus.equals(previousStatus);
        if (unchanged) {
            return authorizationService.applyReadRules(toResponse(instance), decision);
        }
        instance.setProcessKey(nextProcessKey);
        instance.setProcessInstanceId(nextProcessInstanceId);
        instance.setWorkflowStatus(nextStatus);
        LocalDateTime now = LocalDateTime.now();
        instance.setWorkflowBoundAt(instance.getWorkflowBoundAt() != null ? instance.getWorkflowBoundAt() : now);
        instance.setWorkflowStatusUpdatedAt(now);
        instance.setProcessBindingTraceId(traceId(request.getTraceId()));
        instance.setUpdatedBy(userId);
        instance.setUpdatedAt(now);
        applyActionMetadata(instance);
        formInstanceMapper.updateById(instance);
        insertWorkflowAudit(instance, previousStatus, nextStatus, "BIND_PROCESS", userId, now);
        return authorizationService.applyReadRules(toResponse(instance), decision);
    }

    @Transactional
    public FormInstanceResponse updateWorkflowStatus(String formKey,
                                                     String instanceId,
                                                     UpdateWorkflowStatusRequest request) {
        String userId = requireCurrentUser();
        if (request == null || !StringUtils.hasText(request.getWorkflowStatus())) {
            throw new BizException(40014, "FORM_WORKFLOW_STATUS_REQUIRED");
        }
        LcFormInstance instance = formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, instanceId)
                .eq(LcFormInstance::getFormKey, formKey)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
        if (instance == null || !formKey.equals(instance.getFormKey())) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        AuthorizationDecisionResponse decision = authorizationService.requireFormDecision(
                formKey, "EDIT", instance.getId(), List.of());
        if (!authorizationService.canAccessInstance(decision, instance)) {
            throw new BizException(40314, "FORM_WORKFLOW_STATUS_UPDATE_DENIED");
        }
        String previousStatus = instance.getWorkflowStatus();
        String nextStatus = normalizeWorkflowStatus(request.getWorkflowStatus());
        if (!StringUtils.hasText(instance.getProcessInstanceId()) && !STATUS_PENDING_WORKFLOW.equals(nextStatus)) {
            throw new BizException(40913, "FORM_PROCESS_NOT_BOUND");
        }
        if (nextStatus.equals(previousStatus)) {
            return authorizationService.applyReadRules(toResponse(instance), decision);
        }
        LocalDateTime now = LocalDateTime.now();
        instance.setWorkflowStatus(nextStatus);
        instance.setWorkflowStatusUpdatedAt(now);
        instance.setProcessBindingTraceId(traceId(request.getTraceId()));
        instance.setUpdatedBy(userId);
        instance.setUpdatedAt(now);
        applyActionMetadata(instance);
        formInstanceMapper.updateById(instance);
        insertWorkflowAudit(instance, previousStatus, nextStatus, "WORKFLOW_STATUS_UPDATE", userId, now);
        return authorizationService.applyReadRules(toResponse(instance), decision);
    }

    private void insertWorkflowAudit(LcFormInstance instance,
                                     String previousStatus,
                                     String nextStatus,
                                     String changeType,
                                     String userId,
                                     LocalDateTime now) {
        LcFormInstanceWorkflowAudit audit = new LcFormInstanceWorkflowAudit();
        audit.setId(UlidGenerator.nextUlid());
        audit.setTenantId(instance.getTenantId());
        audit.setFormInstanceId(instance.getId());
        audit.setFormKey(instance.getFormKey());
        audit.setProcessKey(instance.getProcessKey());
        audit.setProcessInstanceId(instance.getProcessInstanceId());
        audit.setPreviousWorkflowStatus(previousStatus);
        audit.setWorkflowStatus(nextStatus);
        audit.setChangeType(changeType);
        audit.setTraceId(instance.getProcessBindingTraceId());
        audit.setActionId(instance.getActionId());
        audit.setActionType(instance.getActionType());
        audit.setActionSource(instance.getActionSource());
        audit.setActionActorType(instance.getActionActorType());
        audit.setActionActorId(instance.getActionActorId());
        audit.setActionActorName(instance.getActionActorName());
        audit.setActionCorrelationId(instance.getActionCorrelationId());
        audit.setCreatedBy(userId);
        audit.setCreatedAt(now);
        audit.setUpdatedBy(userId);
        audit.setUpdatedAt(now);
        workflowAuditMapper.insert(audit);
    }

    private String toJson(SubmitFormInstanceRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getData());
        } catch (JsonProcessingException e) {
            throw new BizException(40003, "FORM_INSTANCE_JSON_INVALID");
        }
    }

    private String toJsonRaw(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
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

    private LcFormInstance findVisibleById(String id) {
        return formInstanceMapper.selectOne(new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getId, id)
                .in(LcFormInstance::getTenantId, visibleTenantIds()));
    }

    private String normalizeWorkflowStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
        if (!SUPPORTED_WORKFLOW_STATUSES.contains(normalized)) {
            throw new BizException(40014, "FORM_WORKFLOW_STATUS_UNSUPPORTED");
        }
        return normalized;
    }

    private String traceId(String requestTraceId) {
        if (StringUtils.hasText(requestTraceId)) {
            return requestTraceId.trim();
        }
        return TraceUtil.getTraceId();
    }

    private void applyActionMetadata(LcFormInstance instance) {
        LowcodeActionExecutionContext.Snapshot snapshot = LowcodeActionExecutionContext.current();
        if (snapshot == null) {
            return;
        }
        instance.setActionId(snapshot.actionId());
        instance.setActionType(snapshot.actionType());
        instance.setActionSource(snapshot.source());
        instance.setActionActorType(snapshot.actorType());
        instance.setActionActorId(snapshot.actorId());
        instance.setActionActorName(snapshot.actorName());
        instance.setActionTraceId(snapshot.traceId());
        instance.setActionCorrelationId(snapshot.correlationId());
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

    private FormInstanceResponse toResponse(LcFormInstance instance) {
        FormInstanceResponse response = new FormInstanceResponse();
        response.setId(instance.getId());
        response.setTenantId(instance.getTenantId());
        response.setFormDefinitionId(instance.getFormDefinitionId());
        response.setFormDefinitionVersion(instance.getFormDefinitionVersion());
        response.setSchemaHash(instance.getSchemaHash());
        response.setFormKey(instance.getFormKey());
        response.setStatus(instance.getStatus());
        response.setDataJson(instance.getDataJson());
        response.setSubmittedBy(instance.getSubmittedBy());
        response.setProcessKey(instance.getProcessKey());
        response.setProcessInstanceId(instance.getProcessInstanceId());
        response.setWorkflowStatus(instance.getWorkflowStatus());
        response.setWorkflowBoundAt(instance.getWorkflowBoundAt());
        response.setWorkflowStatusUpdatedAt(instance.getWorkflowStatusUpdatedAt());
        response.setProcessBindingTraceId(instance.getProcessBindingTraceId());
        response.setActionId(instance.getActionId());
        response.setActionType(instance.getActionType());
        response.setActionSource(instance.getActionSource());
        response.setActionActorType(instance.getActionActorType());
        response.setActionActorId(instance.getActionActorId());
        response.setActionActorName(instance.getActionActorName());
        response.setActionTraceId(instance.getActionTraceId());
        response.setActionCorrelationId(instance.getActionCorrelationId());
        response.setSubmittedAt(instance.getSubmittedAt());
        return response;
    }

}
