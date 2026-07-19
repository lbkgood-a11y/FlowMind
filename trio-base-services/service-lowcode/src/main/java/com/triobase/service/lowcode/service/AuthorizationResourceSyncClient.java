package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;

@Component
public class AuthorizationResourceSyncClient {

    private static final String SERVICE_NAME = "service-lowcode";
    private static final List<String> FORM_ACTIONS = List.of(
            "VIEW", "CREATE", "EDIT", "DELETE", "SUBMIT", "APPROVE", "REJECT",
            "EXPORT", "DESIGN", "PUBLISH", "OFFLINE", "FIELD_READ", "FIELD_WRITE");

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient restClient;

    @Autowired
    public AuthorizationResourceSyncClient(InternalServiceSecurityProperties securityProperties,
                                           @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this(securityProperties, RestClient.builder().baseUrl(authBaseUrl).build());
    }

    AuthorizationResourceSyncClient(InternalServiceSecurityProperties securityProperties, RestClient restClient) {
        this.securityProperties = securityProperties;
        this.restClient = restClient;
    }

    public void syncPublishedForm(LcFormDefinition definition, List<FormFieldSchemaRequest> fields) {
        if (definition == null || !StringUtils.hasText(definition.getTenantId())
                || !StringUtils.hasText(definition.getFormKey())) {
            throw new BizException(40090, "LOWCODE_AUTHZ_SYNC_FORM_REQUIRED");
        }
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId(definition.getTenantId());
        request.setOwnerService(SERVICE_NAME);
        request.setResources(List.of(formResource(definition, fields)));
        postSync(request);
    }

    public void syncOfflineForm(LcFormDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getTenantId())
                || !StringUtils.hasText(definition.getFormKey())) {
            return;
        }
        AuthorizationResourceSyncRequest.Resource resource = formResource(definition, List.of());
        resource.setLifecycleStatus("INACTIVE");
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId(definition.getTenantId());
        request.setOwnerService(SERVICE_NAME);
        request.setResources(List.of(resource));
        postSync(request);
    }

    public void syncOfflineApplication(LcApplicationVersion version) {
        if (version == null || !StringUtils.hasText(version.getTenantId())
                || !StringUtils.hasText(version.getAppKey())) {
            return;
        }
        AuthorizationResourceSyncRequest.Resource resource = appResource(version, List.of(), List.of());
        resource.setLifecycleStatus("INACTIVE");
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId(version.getTenantId());
        request.setOwnerService(SERVICE_NAME);
        request.setResources(List.of(resource));
        postSync(request);
    }

    public void syncPublishedApplication(LcApplicationVersion version,
                                         List<ApplicationPageRequest> pages,
                                         List<ApplicationActionRequest> actions) {
        if (version == null || !StringUtils.hasText(version.getTenantId())
                || !StringUtils.hasText(version.getAppKey())) {
            throw new BizException(40090, "LOWCODE_AUTHZ_SYNC_APP_REQUIRED");
        }
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId(version.getTenantId());
        request.setOwnerService(SERVICE_NAME);
        request.setResources(List.of(appResource(version, pages, actions)));
        postSync(request);
    }

    private void postSync(AuthorizationResourceSyncRequest request) {
        JsonNode envelope = restClient.post()
                .uri("/internal/v1/authz/resources/sync")
                .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, SERVICE_NAME)
                .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (envelope == null || envelope.path("code").asInt(-1) != 0) {
            throw new BizException(50290, "LOWCODE_AUTHZ_SYNC_FAILED");
        }
    }

    private AuthorizationResourceSyncRequest.Resource appResource(LcApplicationVersion version,
                                                                  List<ApplicationPageRequest> pages,
                                                                  List<ApplicationActionRequest> actions) {
        AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
        resource.setResourceCode("LOWCODE_APP:" + version.getAppKey().trim().toUpperCase(Locale.ROOT));
        resource.setResourceType("LOWCODE_APP");
        resource.setDisplayName(version.getName());
        resource.setBusinessObjectId(version.getId());
        resource.setLifecycleStatus("ACTIVE");
        resource.setMetadataJson(appMetadata(version, pages, actions));
        resource.setActions(List.of(
                appAction("VIEW"),
                appAction("DESIGN"),
                appAction("PUBLISH"),
                appAction("OFFLINE")
        ));
        return resource;
    }

    private AuthorizationResourceSyncRequest.Resource formResource(LcFormDefinition definition,
                                                                   List<FormFieldSchemaRequest> fields) {
        String formCode = "LOWCODE_FORM:" + definition.getFormKey().trim().toUpperCase(Locale.ROOT);
        AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
        resource.setResourceCode(formCode);
        resource.setResourceType("LOWCODE_FORM");
        resource.setDisplayName(definition.getName());
        resource.setBusinessObjectId(definition.getId());
        resource.setLifecycleStatus("ACTIVE");
        resource.setMetadataJson("{\"formKey\":\"" + escape(definition.getFormKey()) + "\",\"version\":"
                + definition.getVersion() + "}");
        resource.setActions(FORM_ACTIONS.stream()
                .map(action -> action(action))
                .toList());
        resource.setFields(fields == null ? List.of() : fields.stream()
                .map(this::field)
                .toList());
        resource.setGuards(List.of(
                guard("WORKFLOW_CANDIDATE", "service-workflow-engine",
                        "LOWCODE_FORM,WORKFLOW_TASK", "当前用户必须是待办任务候选人或处理人"),
                guard("NO_SELF_APPROVAL", "service-workflow-engine",
                        "LOWCODE_FORM,WORKFLOW_TASK", "发起人不可审批自己的单据"),
                guard("DOCUMENT_STATUS", SERVICE_NAME,
                        "LOWCODE_FORM", "单据状态必须允许当前操作"),
                guard("ARCHIVED_LOCK", SERVICE_NAME,
                        "LOWCODE_FORM", "已归档单据不可编辑")
        ));
        return resource;
    }

    private AuthorizationResourceSyncRequest.Action appAction(String actionCode) {
        AuthorizationResourceSyncRequest.Action action = new AuthorizationResourceSyncRequest.Action();
        action.setActionCode(actionCode);
        action.setActionCategory("APPLICATION");
        action.setDescription("LOWCODE_APP " + actionCode);
        return action;
    }

    private AuthorizationResourceSyncRequest.Action action(String actionCode) {
        AuthorizationResourceSyncRequest.Action action = new AuthorizationResourceSyncRequest.Action();
        action.setActionCode(actionCode);
        action.setActionCategory(actionCode.startsWith("FIELD_") ? "FIELD" : "DOCUMENT");
        action.setDescription("LOWCODE_FORM " + actionCode);
        if ("APPROVE".equals(actionCode) || "REJECT".equals(actionCode)) {
            action.setGuardCodes(List.of("WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL", "DOCUMENT_STATUS"));
        } else if ("EDIT".equals(actionCode) || "DELETE".equals(actionCode) || "OFFLINE".equals(actionCode)) {
            action.setGuardCodes(List.of("DOCUMENT_STATUS", "ARCHIVED_LOCK"));
        }
        return action;
    }

    private AuthorizationResourceSyncRequest.Field field(FormFieldSchemaRequest source) {
        AuthorizationResourceSyncRequest.Field field = new AuthorizationResourceSyncRequest.Field();
        field.setFieldKey(source.getFieldKey());
        field.setFieldLabel(source.getLabel());
        field.setFieldType(source.getFieldType());
        field.setSensitivityClassification(source.getSensitivityClassification());
        field.setDefaultMaskStrategy(source.getDefaultMaskStrategy());
        return field;
    }

    private AuthorizationResourceSyncRequest.Guard guard(String code,
                                                         String ownerService,
                                                         String resourceTypes,
                                                         String description) {
        AuthorizationResourceSyncRequest.Guard guard = new AuthorizationResourceSyncRequest.Guard();
        guard.setGuardCode(code);
        guard.setOwnerService(ownerService);
        guard.setSupportedResourceTypes(resourceTypes);
        guard.setDescription(description);
        return guard;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String appMetadata(LcApplicationVersion version,
                               List<ApplicationPageRequest> pages,
                               List<ApplicationActionRequest> actions) {
        int pageCount = pages != null ? pages.size() : 0;
        int actionCount = actions != null ? actions.size() : 0;
        return "{\"appKey\":\"" + escape(version.getAppKey()) + "\",\"version\":"
                + version.getVersion() + ",\"formKey\":\"" + escape(version.getFormKey())
                + "\",\"pageCount\":" + pageCount + ",\"actionCount\":" + actionCount + "}";
    }
}
