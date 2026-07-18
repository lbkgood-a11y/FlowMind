package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.dto.ApplicationActionResponse;
import com.triobase.service.lowcode.dto.ApplicationPageResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.RuntimeActionRequest;
import com.triobase.service.lowcode.dto.RuntimeActionResponse;
import com.triobase.service.lowcode.dto.RuntimeApplicationDescriptorResponse;
import com.triobase.service.lowcode.dto.RuntimeApplicationSummaryResponse;
import com.triobase.service.lowcode.dto.RuntimeRetryWorkflowRequest;
import com.triobase.service.lowcode.dto.RuntimeWorkflowResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.service.lowcode.entity.LcApplication;
import com.triobase.service.lowcode.entity.LcApplicationAction;
import com.triobase.service.lowcode.entity.LcApplicationPage;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.mapper.ApplicationActionMapper;
import com.triobase.service.lowcode.mapper.ApplicationMapper;
import com.triobase.service.lowcode.mapper.ApplicationPageMapper;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApplicationRuntimeService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_PENDING_WORKFLOW = "PENDING_WORKFLOW";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String GLOBAL_TENANT_ID = "GLOBAL";
    private static final Set<String> CREATE_SAVE_ACTIONS = Set.of("CREATE", "SAVE", "SUBMIT");
    private static final Set<String> WORKFLOW_ACTIONS = Set.of("SUBMIT_AND_LAUNCH_WORKFLOW", "RETRY_WORKFLOW");

    private final ApplicationMapper applicationMapper;
    private final ApplicationVersionMapper applicationVersionMapper;
    private final ApplicationPageMapper applicationPageMapper;
    private final ApplicationActionMapper applicationActionMapper;
    private final FormDefinitionService formDefinitionService;
    private final FormInstanceService formInstanceService;
    private final WorkflowLaunchClient workflowLaunchClient;
    private final ObjectMapper objectMapper;

    public PageResult<RuntimeApplicationSummaryResponse> listAvailable(int page, int size) {
        Map<String, LcApplicationVersion> latestByAppKey = new LinkedHashMap<>();
        applicationVersionMapper.selectList(new LambdaQueryWrapper<LcApplicationVersion>()
                        .in(LcApplicationVersion::getTenantId, visibleTenantIds())
                        .eq(LcApplicationVersion::getStatus, STATUS_PUBLISHED)
                        .orderByDesc(LcApplicationVersion::getVersion))
                .stream()
                .sorted(runtimeVersionComparator())
                .forEach(version -> latestByAppKey.putIfAbsent(version.getAppKey(), version));
        List<RuntimeApplicationSummaryResponse> all = latestByAppKey.values().stream()
                .filter(this::canView)
                .map(this::toSummary)
                .toList();
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= all.size()) {
            return PageResult.empty(page, size);
        }
        int toIndex = Math.min(fromIndex + size, all.size());
        return PageResult.of(all.subList(fromIndex, toIndex), all.size(), page, size);
    }

    public RuntimeApplicationDescriptorResponse descriptor(String appKey, Integer version) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        PublishedFormSnapshotResponse form = formDefinitionService.getPublishedSnapshot(
                applicationVersion.getPrimaryFormDefinitionId());
        RuntimeApplicationDescriptorResponse response = new RuntimeApplicationDescriptorResponse();
        copySummary(response, applicationVersion);
        response.setPrimaryFormDefinitionId(applicationVersion.getPrimaryFormDefinitionId());
        response.setSchemaJson(form.getSchemaJson());
        response.setUiSchemaJson(form.getUiSchemaJson());
        response.setPages(listPages(applicationVersion.getId()).stream().map(this::toPageResponse).toList());
        response.setActions(visibleActions(applicationVersion.getId()).stream().map(this::toActionResponse).toList());
        return response;
    }

    public PageResult<FormInstanceResponse> listInstances(String appKey, Integer version, int page, int size) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        requirePage(applicationVersion.getId(), "LIST");
        return formInstanceService.list(applicationVersion.getFormKey(), page, size);
    }

    public FormInstanceResponse getInstance(String appKey, Integer version, String instanceId) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        FormInstanceResponse response = formInstanceService.getById(instanceId);
        if (!applicationVersion.getFormKey().equals(response.getFormKey())) {
            throw new BizException(40455, "APPLICATION_RUNTIME_INSTANCE_NOT_FOUND");
        }
        return response;
    }

    public RuntimeActionResponse runAction(String appKey,
                                           Integer version,
                                           String actionCode,
                                           RuntimeActionRequest request) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        LcApplicationAction action = requireAction(applicationVersion.getId(), actionCode);
        String actionType = normalize(action.getActionType());
        if (CREATE_SAVE_ACTIONS.contains(actionType)) {
            FormInstanceResponse instance = submitForm(applicationVersion, request);
            return savedResponse(action.getActionCode(), instance);
        }
        if ("SUBMIT_AND_LAUNCH_WORKFLOW".equals(actionType)) {
            FormInstanceResponse instance = submitForm(applicationVersion, request);
            return launchWorkflow(applicationVersion, action, request, instance, false);
        }
        throw new BizException(40055, "APPLICATION_RUNTIME_ACTION_UNSUPPORTED");
    }

    public RuntimeActionResponse retryWorkflow(String appKey,
                                               Integer version,
                                               String instanceId,
                                               RuntimeRetryWorkflowRequest request) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        FormInstanceResponse instance = getInstance(appKey, version, instanceId);
        if (!STATUS_PENDING_WORKFLOW.equals(instance.getWorkflowStatus())) {
            throw new BizException(40955, "APPLICATION_RUNTIME_WORKFLOW_NOT_PENDING");
        }
        LcApplicationAction action = resolveRetryAction(applicationVersion.getId(), request);
        RuntimeActionRequest actionRequest = new RuntimeActionRequest();
        actionRequest.setIdempotencyKey(request != null ? request.getIdempotencyKey() : null);
        actionRequest.setData(readData(instance.getDataJson()));
        return launchWorkflow(applicationVersion, action, actionRequest, instance, true);
    }

    private FormInstanceResponse submitForm(LcApplicationVersion applicationVersion,
                                            RuntimeActionRequest request) {
        SubmitFormInstanceRequest submitRequest = new SubmitFormInstanceRequest();
        submitRequest.setData(request != null ? request.getData() : Map.of());
        return formInstanceService.submit(applicationVersion.getFormKey(), submitRequest);
    }

    private RuntimeActionResponse launchWorkflow(LcApplicationVersion applicationVersion,
                                                 LcApplicationAction action,
                                                 RuntimeActionRequest request,
                                                 FormInstanceResponse instance,
                                                 boolean retry) {
        try {
            RuntimeWorkflowResponse workflow = workflowLaunchClient.start(startCommand(
                    applicationVersion, action, request, instance));
            BindFormProcessRequest bind = new BindFormProcessRequest();
            bind.setProcessKey(action.getProcessKey());
            bind.setProcessInstanceId(workflow.getProcessInstanceId());
            bind.setWorkflowStatus(workflow.getStatus());
            FormInstanceResponse bound = formInstanceService.bindProcess(
                    applicationVersion.getFormKey(), instance.getId(), bind);
            RuntimeActionResponse response = new RuntimeActionResponse();
            response.setActionCode(action.getActionCode());
            response.setStatus("WORKFLOW_STARTED");
            response.setFormInstance(bound);
            response.setWorkflow(workflow);
            return response;
        } catch (WorkflowLaunchException exception) {
            markPending(applicationVersion, instance);
            if (retry && exception.isVersionConflict()) {
                throw new BizException(40955, "APPLICATION_WORKFLOW_VERSION_CONFLICT");
            }
            RuntimeActionResponse response = new RuntimeActionResponse();
            response.setActionCode(action.getActionCode());
            response.setStatus("WORKFLOW_PENDING");
            response.setFormInstance(formInstanceService.getById(instance.getId()));
            response.setRetryable(!exception.isVersionConflict());
            response.setErrorCode(exception.getErrorCode());
            response.setMessage(exception.getMessage());
            return response;
        }
    }

    private WorkflowLaunchClient.StartCommand startCommand(LcApplicationVersion version,
                                                           LcApplicationAction action,
                                                           RuntimeActionRequest request,
                                                           FormInstanceResponse instance) {
        JsonNode metadata = actionMetadata(action);
        Integer processVersion = metadata.path("processVersion").isInt()
                ? metadata.path("processVersion").asInt() : null;
        String processPackageId = metadata.path("processPackageId").asText(null);
        String launchMode = text(metadata, "launchMode", "EXISTING_DOCUMENT");
        String businessType = text(metadata, "businessType", version.getFormKey());
        String businessId = text(metadata, "businessId", instance.getId());
        return new WorkflowLaunchClient.StartCommand(
                processPackageId,
                processVersion,
                action.getProcessKey(),
                request != null ? request.getTitle() : null,
                request != null ? request.getData() : readData(instance.getDataJson()),
                launchMode,
                businessType,
                businessId,
                idempotencyKey(version, action, request, instance));
    }

    private RuntimeActionResponse savedResponse(String actionCode, FormInstanceResponse instance) {
        RuntimeActionResponse response = new RuntimeActionResponse();
        response.setActionCode(actionCode);
        response.setStatus("FORM_SAVED");
        response.setFormInstance(instance);
        return response;
    }

    private void markPending(LcApplicationVersion applicationVersion, FormInstanceResponse instance) {
        UpdateWorkflowStatusRequest update = new UpdateWorkflowStatusRequest();
        update.setWorkflowStatus(STATUS_PENDING_WORKFLOW);
        formInstanceService.updateWorkflowStatus(applicationVersion.getFormKey(), instance.getId(), update);
    }

    private LcApplicationAction resolveRetryAction(String versionId, RuntimeRetryWorkflowRequest request) {
        List<LcApplicationAction> actions = visibleActions(versionId).stream()
                .filter(action -> WORKFLOW_ACTIONS.contains(normalize(action.getActionType())))
                .toList();
        if (request != null && StringUtils.hasText(request.getActionCode())) {
            return actions.stream()
                    .filter(action -> action.getActionCode().equals(request.getActionCode().trim()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(40455, "APPLICATION_RUNTIME_ACTION_NOT_FOUND"));
        }
        if (actions.size() != 1) {
            throw new BizException(40055, "APPLICATION_RUNTIME_RETRY_ACTION_REQUIRED");
        }
        return actions.getFirst();
    }

    private LcApplicationAction requireAction(String versionId, String actionCode) {
        if (!StringUtils.hasText(actionCode)) {
            throw new BizException(40055, "APPLICATION_RUNTIME_ACTION_REQUIRED");
        }
        return visibleActions(versionId).stream()
                .filter(action -> action.getActionCode().equals(actionCode.trim()))
                .findFirst()
                .orElseThrow(() -> new BizException(40455, "APPLICATION_RUNTIME_ACTION_NOT_FOUND"));
    }

    private List<LcApplicationAction> visibleActions(String versionId) {
        return listActions(versionId).stream()
                .filter(action -> STATUS_ENABLED.equalsIgnoreCase(action.getStatus()))
                .filter(action -> !StringUtils.hasText(action.getPermissionCode()) || hasPermission(action.getPermissionCode()))
                .toList();
    }

    private LcApplicationVersion requireRuntimeVersion(String appKey, Integer version) {
        LcApplicationVersion applicationVersion = findRuntimeVersion(appKey, version);
        if (applicationVersion == null) {
            throw new BizException(40455, "APPLICATION_RUNTIME_NOT_FOUND");
        }
        if (!canView(applicationVersion)) {
            throw new BizException(40355, "APPLICATION_RUNTIME_PERMISSION_DENIED");
        }
        return applicationVersion;
    }

    private LcApplicationVersion findRuntimeVersion(String appKey, Integer version) {
        if (!StringUtils.hasText(appKey)) {
            return null;
        }
        LambdaQueryWrapper<LcApplicationVersion> query = new LambdaQueryWrapper<LcApplicationVersion>()
                .in(LcApplicationVersion::getTenantId, visibleTenantIds())
                .eq(LcApplicationVersion::getAppKey, appKey.trim())
                .eq(LcApplicationVersion::getStatus, STATUS_PUBLISHED);
        if (version != null) {
            query.eq(LcApplicationVersion::getVersion, version);
        }
        return applicationVersionMapper.selectList(query).stream()
                .sorted(runtimeVersionComparator())
                .findFirst()
                .orElse(null);
    }

    private Comparator<LcApplicationVersion> runtimeVersionComparator() {
        return Comparator.comparing((LcApplicationVersion item) ->
                        currentTenantId().equals(item.getTenantId()) ? 0 : 1)
                .thenComparing(LcApplicationVersion::getVersion, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean canView(LcApplicationVersion version) {
        return StringUtils.hasText(version.getViewPermissionCode())
                && hasPermission(version.getViewPermissionCode());
    }

    private boolean hasPermission(String required) {
        List<String> permissions = SecurityContextHolder.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.stream()
                .filter(StringUtils::hasText)
                .anyMatch(granted -> grantedMatches(granted, required));
    }

    private boolean grantedMatches(String granted, String required) {
        if (granted.equals(required)) {
            return true;
        }
        if (!granted.contains("*")) {
            return false;
        }
        String regex = Pattern.quote(granted).replace("*", "\\E.*\\Q");
        return Pattern.compile(regex).matcher(required).matches();
    }

    private void requirePage(String versionId, String pageType) {
        boolean exists = listPages(versionId).stream()
                .anyMatch(page -> pageType.equalsIgnoreCase(page.getPageType()));
        if (!exists) {
            throw new BizException(40455, "APPLICATION_RUNTIME_PAGE_NOT_FOUND");
        }
    }

    private RuntimeApplicationSummaryResponse toSummary(LcApplicationVersion version) {
        RuntimeApplicationSummaryResponse response = new RuntimeApplicationSummaryResponse();
        copySummary(response, version);
        return response;
    }

    private void copySummary(RuntimeApplicationSummaryResponse response, LcApplicationVersion version) {
        LcApplication application = applicationMapper.selectById(version.getApplicationId());
        response.setTenantId(version.getTenantId());
        response.setAppKey(version.getAppKey());
        response.setName(version.getName());
        response.setDescription(version.getDescription());
        response.setVersionId(version.getId());
        response.setVersion(version.getVersion());
        response.setFormKey(version.getFormKey());
        response.setFormVersion(version.getFormVersion());
        response.setSchemaHash(version.getSchemaHash());
        response.setMetadataHash(version.getMetadataHash());
        response.setPublishedAt(version.getPublishedAt());
        if (application != null) {
            response.setName(application.getName());
            response.setDescription(application.getDescription());
        }
    }

    private ApplicationPageResponse toPageResponse(LcApplicationPage page) {
        ApplicationPageResponse response = new ApplicationPageResponse();
        response.setId(page.getId());
        response.setPageType(page.getPageType());
        response.setMetadataJson(page.getMetadataJson());
        response.setSortOrder(page.getSortOrder());
        return response;
    }

    private ApplicationActionResponse toActionResponse(LcApplicationAction action) {
        ApplicationActionResponse response = new ApplicationActionResponse();
        response.setId(action.getId());
        response.setActionCode(action.getActionCode());
        response.setActionType(action.getActionType());
        response.setLabel(action.getLabel());
        response.setPermissionCode(action.getPermissionCode());
        response.setFormDefinitionId(action.getFormDefinitionId());
        response.setProcessKey(action.getProcessKey());
        response.setMetadataJson(action.getMetadataJson());
        response.setStatus(action.getStatus());
        response.setSortOrder(action.getSortOrder());
        return response;
    }

    private List<LcApplicationPage> listPages(String versionId) {
        return applicationPageMapper.selectList(new LambdaQueryWrapper<LcApplicationPage>()
                .eq(LcApplicationPage::getApplicationVersionId, versionId)
                .orderByAsc(LcApplicationPage::getSortOrder));
    }

    private List<LcApplicationAction> listActions(String versionId) {
        return applicationActionMapper.selectList(new LambdaQueryWrapper<LcApplicationAction>()
                .eq(LcApplicationAction::getApplicationVersionId, versionId)
                .orderByAsc(LcApplicationAction::getSortOrder));
    }

    private JsonNode actionMetadata(LcApplicationAction action) {
        if (!StringUtils.hasText(action.getMetadataJson())) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(action.getMetadataJson());
        } catch (Exception e) {
            throw new BizException(40055, "APPLICATION_RUNTIME_ACTION_METADATA_INVALID");
        }
    }

    private Map<String, Object> readData(String dataJson) {
        if (!StringUtils.hasText(dataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException(40055, "APPLICATION_RUNTIME_DATA_INVALID");
        }
    }

    private String idempotencyKey(LcApplicationVersion version,
                                  LcApplicationAction action,
                                  RuntimeActionRequest request,
                                  FormInstanceResponse instance) {
        if (request != null && StringUtils.hasText(request.getIdempotencyKey())) {
            return request.getIdempotencyKey().trim();
        }
        return "lowcode:%s:%s:%s:%s".formatted(
                version.getAppKey(), version.getVersion(), action.getActionCode(), instance.getId());
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        String value = node.path(fieldName).asText(null);
        return StringUtils.hasText(value) ? value.trim() : fallback;
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
