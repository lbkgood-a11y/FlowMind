package com.triobase.service.openapi.service.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CustomDocumentAuthorizationSyncClient {

    private final InternalServiceSecurityProperties securityProperties;
    private final RestClient restClient;

    @Autowired
    public CustomDocumentAuthorizationSyncClient(
            InternalServiceSecurityProperties securityProperties,
            @Value("${triobase.integrations.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this(securityProperties, RestClient.builder().baseUrl(authBaseUrl).build());
    }

    CustomDocumentAuthorizationSyncClient(InternalServiceSecurityProperties securityProperties, RestClient restClient) {
        this.securityProperties = securityProperties;
        this.restClient = restClient;
    }

    public void sync(CustomDocumentAuthorizationManifest manifest) {
        AuthorizationResourceSyncRequest request = toSyncRequest(manifest);
        JsonNode envelope = restClient.post()
                .uri("/internal/v1/authz/resources/sync")
                .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, manifest.getServiceName())
                .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, securityProperties.getToken())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (envelope == null || envelope.path("code").asInt(-1) != 0) {
            throw new BizException(50290, "CUSTOM_DOC_AUTHZ_SYNC_FAILED");
        }
    }

    AuthorizationResourceSyncRequest toSyncRequest(CustomDocumentAuthorizationManifest manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.getTenantId())
                || !StringUtils.hasText(manifest.getServiceName())) {
            throw new BizException(40090, "CUSTOM_DOC_AUTHZ_MANIFEST_REQUIRED");
        }
        AuthorizationResourceSyncRequest request = new AuthorizationResourceSyncRequest();
        request.setTenantId(manifest.getTenantId().trim());
        request.setOwnerService(manifest.getServiceName().trim());
        request.setResources(manifest.getDocuments() == null ? List.of() : manifest.getDocuments().stream()
                .map(document -> resource(manifest.getServiceName().trim(), document))
                .toList());
        return request;
    }

    private AuthorizationResourceSyncRequest.Resource resource(
            String serviceName,
            CustomDocumentAuthorizationManifest.Document document) {
        if (document == null || !StringUtils.hasText(document.getCode())
                || !document.getCode().startsWith("CUSTOM_DOC:")) {
            throw new BizException(40090, "CUSTOM_DOC_AUTHZ_RESOURCE_CODE_INVALID");
        }
        AuthorizationResourceSyncRequest.Resource resource = new AuthorizationResourceSyncRequest.Resource();
        resource.setResourceCode(document.getCode().trim());
        resource.setResourceType("CUSTOM_DOC");
        resource.setDisplayName(document.getDisplayName());
        resource.setBusinessObjectId(document.getBusinessObjectId());
        resource.setLifecycleStatus(StringUtils.hasText(document.getLifecycleStatus())
                ? document.getLifecycleStatus().trim() : "ACTIVE");
        resource.setMetadataJson(document.getMetadataJson());
        resource.setActions(document.getActions() == null ? List.of() : document.getActions().stream()
                .map(this::action)
                .toList());
        resource.setFields(document.getFields() == null ? List.of() : document.getFields().stream()
                .map(this::field)
                .toList());
        resource.setGuards(document.getGuards() == null ? List.of() : document.getGuards().stream()
                .map(guard -> guard(serviceName, guard))
                .toList());
        return resource;
    }

    private AuthorizationResourceSyncRequest.Action action(CustomDocumentAuthorizationManifest.Action source) {
        AuthorizationResourceSyncRequest.Action action = new AuthorizationResourceSyncRequest.Action();
        action.setActionCode(source.getActionCode());
        action.setActionCategory("DOCUMENT");
        action.setDescription(source.getDescription());
        action.setGuardCodes(source.getGuardCodes() != null ? source.getGuardCodes() : List.of());
        return action;
    }

    private AuthorizationResourceSyncRequest.Field field(CustomDocumentAuthorizationManifest.Field source) {
        AuthorizationResourceSyncRequest.Field field = new AuthorizationResourceSyncRequest.Field();
        field.setFieldKey(source.getFieldKey());
        field.setFieldLabel(source.getFieldLabel());
        field.setFieldType(source.getFieldType());
        field.setSensitivityClassification(source.getSensitivityClassification());
        field.setDefaultMaskStrategy(source.getDefaultMaskStrategy());
        return field;
    }

    private AuthorizationResourceSyncRequest.Guard guard(
            String serviceName,
            CustomDocumentAuthorizationManifest.Guard source) {
        AuthorizationResourceSyncRequest.Guard guard = new AuthorizationResourceSyncRequest.Guard();
        guard.setGuardCode(source.getGuardCode());
        guard.setOwnerService(StringUtils.hasText(source.getOwnerService()) ? source.getOwnerService() : serviceName);
        guard.setSupportedResourceTypes(source.getSupportedResourceTypes());
        guard.setConfigSchemaJson(source.getConfigSchemaJson());
        guard.setDescription(source.getDescription());
        return guard;
    }
}
