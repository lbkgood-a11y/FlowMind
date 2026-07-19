package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionError;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.dto.authz.AuthzDecisionReason;
import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
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
import com.triobase.service.lowcode.dto.RuntimeApplicationDescriptorResponse;
import com.triobase.service.lowcode.dto.RuntimeApplicationSummaryResponse;
import com.triobase.service.lowcode.dto.RuntimeFieldAuthorizationResponse;
import com.triobase.service.lowcode.dto.RuntimeRetryWorkflowRequest;
import com.triobase.service.lowcode.dto.RuntimeWorkflowResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.common.dto.authz.AuthzGuardRequirement;
import com.triobase.service.lowcode.dto.GuardRequirementResponse;
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
    private final LowcodeAuthorizationService authorizationService;
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
        List<LcApplicationPage> pages = listPages(applicationVersion.getId());
        List<LcApplicationAction> actions = enabledActions(applicationVersion.getId());
        RuntimeDescriptorAuthorization authorization = descriptorAuthorization(applicationVersion, form, pages, actions);
        RuntimeApplicationDescriptorResponse response = new RuntimeApplicationDescriptorResponse();
        copySummary(response, applicationVersion);
        response.setPrimaryFormDefinitionId(applicationVersion.getPrimaryFormDefinitionId());
        response.setSchemaJson(form.getSchemaJson());
        response.setUiSchemaJson(form.getUiSchemaJson());
        response.setPages(authorization.pages());
        response.setActions(authorization.actions());
        response.setFieldRules(authorization.fieldRules());
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

    public GlobalActionResult executeLocalAction(String appKey,
                                                 Integer version,
                                                 String actionCode,
                                                 GlobalActionRequest request) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        LcApplicationAction action = requireAction(applicationVersion, actionCode);
        String actionType = normalize(action.getActionType());
        if (CREATE_SAVE_ACTIONS.contains(actionType)) {
            FormInstanceResponse instance = submitForm(applicationVersion, request,
                    LowcodeAuthorizationActionCatalog.formActionForApplicationActionType(actionType));
            return savedResult(request, action.getActionCode(), instance);
        }
        if ("SUBMIT_AND_LAUNCH_WORKFLOW".equals(actionType)) {
            FormInstanceResponse instance = submitForm(applicationVersion, request, "SUBMIT");
            return launchWorkflow(applicationVersion, action, request, instance, false);
        }
        throw new BizException(40055, "APPLICATION_RUNTIME_ACTION_UNSUPPORTED");
    }

    public GlobalActionResult executeLocalWorkflowRetry(String appKey,
                                                        Integer version,
                                                        String instanceId,
                                                        RuntimeRetryWorkflowRequest request) {
        LcApplicationVersion applicationVersion = requireRuntimeVersion(appKey, version);
        FormInstanceResponse instance = getInstance(appKey, version, instanceId);
        if (!STATUS_PENDING_WORKFLOW.equals(instance.getWorkflowStatus())) {
            throw new BizException(40955, "APPLICATION_RUNTIME_WORKFLOW_NOT_PENDING");
        }
        LcApplicationAction action = resolveRetryAction(applicationVersion, request);
        GlobalActionRequest actionRequest = new GlobalActionRequest();
        actionRequest.setIdempotencyKey(request != null ? request.getIdempotencyKey() : null);
        actionRequest.setPayload(Map.of("data", readData(instance.getDataJson())));
        return launchWorkflow(applicationVersion, action, actionRequest, instance, true);
    }

    private FormInstanceResponse submitForm(LcApplicationVersion applicationVersion,
                                            GlobalActionRequest request,
                                            String actionCode) {
        SubmitFormInstanceRequest submitRequest = new SubmitFormInstanceRequest();
        submitRequest.setData(actionData(request));
        return formInstanceService.submit(applicationVersion.getFormKey(), submitRequest, actionCode);
    }

    private GlobalActionResult launchWorkflow(LcApplicationVersion applicationVersion,
                                              LcApplicationAction action,
                                              GlobalActionRequest request,
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
            return result(request, ActionStatus.SUCCEEDED, action.getActionCode(), "WORKFLOW_STARTED",
                    bound, workflow, false, null, null);
        } catch (WorkflowLaunchException exception) {
            markPending(applicationVersion, instance);
            if (retry && exception.isVersionConflict()) {
                throw new BizException(40955, "APPLICATION_WORKFLOW_VERSION_CONFLICT");
            }
            return result(request, ActionStatus.FAILED, action.getActionCode(), "WORKFLOW_PENDING",
                    formInstanceService.getById(instance.getId()), null, !exception.isVersionConflict(),
                    exception.getErrorCode(), exception.getMessage());
        }
    }

    private WorkflowLaunchClient.StartCommand startCommand(LcApplicationVersion version,
                                                           LcApplicationAction action,
                                                           GlobalActionRequest request,
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
                textPayload(request, "title"),
                request != null ? actionData(request) : readData(instance.getDataJson()),
                launchMode,
                businessType,
                businessId,
                idempotencyKey(version, action, request, instance));
    }

    private GlobalActionResult savedResult(GlobalActionRequest request,
                                           String actionCode,
                                           FormInstanceResponse instance) {
        return result(request, ActionStatus.SUCCEEDED, actionCode, "FORM_SAVED",
                instance, null, false, null, null);
    }

    private GlobalActionResult result(GlobalActionRequest request,
                                      ActionStatus status,
                                      String actionCode,
                                      String runtimeStatus,
                                      FormInstanceResponse formInstance,
                                      RuntimeWorkflowResponse workflow,
                                      boolean retryable,
                                      String errorCode,
                                      String message) {
        GlobalActionResult result = new GlobalActionResult();
        if (request != null) {
            result.setActionId(request.getActionId());
            result.setActionType(request.getActionType());
        }
        result.setStatus(status);
        result.setOwnerService("service-lowcode");
        result.setRetryable(retryable);
        result.setMessage(message);
        if (workflow != null) {
            result.setOwnerExecutionRef(workflow.getProcessInstanceId());
        } else if (formInstance != null) {
            result.setOwnerExecutionRef(formInstance.getId());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actionCode", actionCode);
        data.put("runtimeStatus", runtimeStatus);
        data.put("formInstance", formInstance);
        data.put("workflow", workflow);
        result.setData(data);
        if (StringUtils.hasText(errorCode) || StringUtils.hasText(message)) {
            result.setErrors(List.of(ActionError.of(
                    StringUtils.hasText(errorCode) ? errorCode : "LOWCODE_ACTION_FAILED",
                    ActionErrorCategory.EXECUTION,
                    StringUtils.hasText(message) ? message : runtimeStatus)));
        }
        return result;
    }

    private void markPending(LcApplicationVersion applicationVersion, FormInstanceResponse instance) {
        UpdateWorkflowStatusRequest update = new UpdateWorkflowStatusRequest();
        update.setWorkflowStatus(STATUS_PENDING_WORKFLOW);
        formInstanceService.updateWorkflowStatus(applicationVersion.getFormKey(), instance.getId(), update);
    }

    private LcApplicationAction resolveRetryAction(LcApplicationVersion version, RuntimeRetryWorkflowRequest request) {
        List<LcApplicationAction> actions = visibleActions(version).stream()
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

    private LcApplicationAction requireAction(LcApplicationVersion version, String actionCode) {
        if (!StringUtils.hasText(actionCode)) {
            throw new BizException(40055, "APPLICATION_RUNTIME_ACTION_REQUIRED");
        }
        return visibleActions(version).stream()
                .filter(action -> action.getActionCode().equals(actionCode.trim()))
                .findFirst()
                .orElseThrow(() -> new BizException(40455, "APPLICATION_RUNTIME_ACTION_NOT_FOUND"));
    }

    private List<LcApplicationAction> visibleActions(LcApplicationVersion version) {
        return enabledActions(version.getId()).stream()
                .filter(action -> actionAllowed(version, action))
                .toList();
    }

    private List<LcApplicationAction> enabledActions(String versionId) {
        return listActions(versionId).stream()
                .filter(action -> STATUS_ENABLED.equalsIgnoreCase(action.getStatus()))
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
        AuthorizationDecisionResponse decision = authorizationService.decideResource(
                authorizationService.appResourceCode(version.getAppKey()), "VIEW", version.getId(), List.of());
        return decisionAllowedWithLegacyFallback(decision, version.getViewPermissionCode());
    }

    private boolean actionAllowed(LcApplicationVersion version, LcApplicationAction action) {
        String authorizationActionCode;
        try {
            authorizationActionCode = LowcodeAuthorizationActionCatalog.formActionForApplicationActionType(
                    action.getActionType());
        } catch (BizException exception) {
            return false;
        }
        AuthorizationDecisionResponse decision = authorizationService.decideForm(
                version.getFormKey(), authorizationActionCode, action.getFormDefinitionId(), List.of());
        return decisionAllowedWithLegacyFallback(decision, action.getPermissionCode());
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

    private RuntimeDescriptorAuthorization descriptorAuthorization(LcApplicationVersion version,
                                                                  PublishedFormSnapshotResponse form,
                                                                  List<LcApplicationPage> pages,
                                                                  List<LcApplicationAction> actions) {
        List<String> fieldKeys = schemaFieldKeys(form.getSchemaJson());
        String appResourceCode = authorizationService.appResourceCode(version.getAppKey());
        String formResourceCode = authorizationService.formResourceCode(version.getFormKey());
        List<AuthorizationDecisionRequest> requests = new ArrayList<>();
        int fieldDecisionIndex = requests.size();
        requests.add(authorizationService.decisionRequest(
                formResourceCode, "VIEW", version.getPrimaryFormDefinitionId(), fieldKeys));
        int pageDecisionStart = requests.size();
        for (LcApplicationPage page : pages) {
            requests.add(authorizationService.decisionRequest(appResourceCode, "VIEW", page.getId(), List.of()));
        }
        int actionDecisionStart = requests.size();
        for (LcApplicationAction action : actions) {
            requests.add(authorizationService.decisionRequest(
                    formResourceCode,
                    LowcodeAuthorizationActionCatalog.formActionForApplicationActionType(action.getActionType()),
                    action.getFormDefinitionId(),
                    List.of()));
        }

        AuthorizationBatchDecisionResponse batch = authorizationService.batchDecide(requests);
        List<AuthorizationDecisionResponse> decisions = batch != null && batch.getDecisions() != null
                ? batch.getDecisions() : List.of();
        AuthorizationDecisionResponse fieldDecision = decisionAt(decisions, fieldDecisionIndex);
        List<ApplicationPageResponse> allowedPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            AuthorizationDecisionResponse decision = decisionAt(decisions, pageDecisionStart + i);
            if (decisionAllowedWithLegacyFallback(decision, version.getViewPermissionCode())) {
                ApplicationPageResponse pageResponse = toPageResponse(pages.get(i));
                pageResponse.setAllowed(true);
                pageResponse.setAuthorizationActionCode("VIEW");
                allowedPages.add(pageResponse);
            }
        }
        List<ApplicationActionResponse> allowedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            LcApplicationAction action = actions.get(i);
            AuthorizationDecisionResponse decision = decisionAt(decisions, actionDecisionStart + i);
            if (decisionAllowedWithLegacyFallback(decision, action.getPermissionCode())) {
                ApplicationActionResponse actionResponse = toActionResponse(action);
                actionResponse.setAllowed(true);
                actionResponse.setAuthorizationActionCode(
                        LowcodeAuthorizationActionCatalog.formActionForApplicationActionType(action.getActionType()));
                actionResponse.setGuardRequirements(guardRequirements(decision));
                allowedActions.add(actionResponse);
            }
        }
        return new RuntimeDescriptorAuthorization(
                allowedPages,
                allowedActions,
                fieldRules(fieldDecision)
        );
    }

    private AuthorizationDecisionResponse decisionAt(List<AuthorizationDecisionResponse> decisions, int index) {
        return index >= 0 && index < decisions.size() ? decisions.get(index) : null;
    }

    private boolean decisionAllowedWithLegacyFallback(AuthorizationDecisionResponse decision, String legacyPermissionCode) {
        if (decision != null && decision.isAllowed()) {
            return true;
        }
        if (hasExplicitDeny(decision)) {
            return false;
        }
        return StringUtils.hasText(legacyPermissionCode) && hasPermission(legacyPermissionCode);
    }

    private boolean hasExplicitDeny(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getReasons() == null) {
            return false;
        }
        return decision.getReasons().stream()
                .map(AuthzDecisionReason::getCode)
                .anyMatch("AUTHZ_DENY_GRANT_MATCHED"::equals);
    }

    private List<RuntimeFieldAuthorizationResponse> fieldRules(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getFieldRules() == null) {
            return List.of();
        }
        return decision.getFieldRules().stream()
                .map(this::toFieldRuleResponse)
                .toList();
    }

    private RuntimeFieldAuthorizationResponse toFieldRuleResponse(AuthzFieldRule rule) {
        RuntimeFieldAuthorizationResponse response = new RuntimeFieldAuthorizationResponse();
        response.setFieldKey(rule.getFieldKey());
        response.setReadMode(rule.getReadMode());
        response.setWriteMode(rule.getWriteMode());
        response.setMaskStrategy(rule.getMaskStrategy());
        response.setReasonCode(rule.getReasonCode());
        response.setReasonMessage(rule.getReasonMessage());
        return response;
    }

    private List<GuardRequirementResponse> guardRequirements(AuthorizationDecisionResponse decision) {
        if (decision == null || decision.getGuardRequirements() == null) {
            return List.of();
        }
        return decision.getGuardRequirements().stream()
                .map(this::toGuardRequirementResponse)
                .toList();
    }

    private GuardRequirementResponse toGuardRequirementResponse(AuthzGuardRequirement requirement) {
        GuardRequirementResponse response = new GuardRequirementResponse();
        response.setGuardCode(requirement.getGuardCode());
        response.setOwnerService(requirement.getOwnerService());
        response.setDescription(requirement.getDescription());
        return response;
    }

    private List<String> schemaFieldKeys(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return List.of();
        }
        try {
            JsonNode properties = objectMapper.readTree(schemaJson).path("properties");
            if (!properties.isObject()) {
                return List.of();
            }
            List<String> fields = new ArrayList<>();
            properties.fieldNames().forEachRemaining(fields::add);
            return fields;
        } catch (Exception exception) {
            throw new BizException(40055, "APPLICATION_RUNTIME_SCHEMA_INVALID");
        }
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

    private Map<String, Object> actionData(GlobalActionRequest request) {
        if (request == null || request.getPayload() == null) {
            return Map.of();
        }
        Object data = request.getPayload().get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return Map.of();
    }

    private String textPayload(GlobalActionRequest request, String key) {
        if (request == null || request.getPayload() == null) {
            return null;
        }
        Object value = request.getPayload().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String idempotencyKey(LcApplicationVersion version,
                                  LcApplicationAction action,
                                  GlobalActionRequest request,
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

    private record RuntimeDescriptorAuthorization(
            List<ApplicationPageResponse> pages,
            List<ApplicationActionResponse> actions,
            List<RuntimeFieldAuthorizationResponse> fieldRules
    ) {
    }
}
