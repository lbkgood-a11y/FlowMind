package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ConnectorEndpoint;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.ConnectorVersionResponse;
import com.triobase.service.openapi.dto.ConnectorVersionMutationRequest;
import com.triobase.service.openapi.dto.CreateConnectorRequest;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorEndpointMapper;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConnectorRegistryService {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private final ConnectorEndpointMapper endpointMapper;
    private final ConnectorVersionMapper versionMapper;
    private final OutboundTargetPolicy targetPolicy;
    private final IntegrationAuditService auditService;

    @Transactional
    public ConnectorVersionResponse create(CreateConnectorRequest request) {
        validate(request);
        String tenantId = resolveTargetTenant(request.tenantId());
        if (endpointMapper.selectCount(new LambdaQueryWrapper<ConnectorEndpoint>()
                .eq(ConnectorEndpoint::getConnectorKey, request.connectorKey().trim())
                .eq(tenantId != null, ConnectorEndpoint::getTenantId, tenantId)
                .isNull(tenantId == null, ConnectorEndpoint::getTenantId)) > 0) {
            throw new BizException(40930, "OPENAPI_CONNECTOR_ALREADY_EXISTS");
        }
        targetPolicy.validate(request.baseUrl(), request.networkPolicy());
        LocalDateTime now = LocalDateTime.now();
        ConnectorEndpoint endpoint = new ConnectorEndpoint();
        endpoint.setId(UlidGenerator.nextUlid());
        endpoint.setTenantId(tenantId);
        endpoint.setConnectorKey(request.connectorKey().trim());
        endpoint.setDisplayName(request.displayName().trim());
        endpoint.setOwnerId(request.ownerId());
        endpoint.setLifecycleState(AssetLifecycleState.ACTIVE);
        endpoint.setRowVersion(0L);
        endpoint.setCreatedBy(currentOperator());
        endpoint.setCreatedAt(now);
        endpoint.setUpdatedBy(currentOperator());
        endpoint.setUpdatedAt(now);
        endpointMapper.insert(endpoint);

        ConnectorVersion version = new ConnectorVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setConnectorEndpointId(endpoint.getId());
        version.setVersionNumber(1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        version.setBaseUrl(request.baseUrl());
        version.setOperationPath(request.operationPath());
        version.setHttpMethod(request.httpMethod().toUpperCase(Locale.ROOT));
        version.setTimeoutMillis(request.timeoutMillis());
        version.setOperationClass(request.operationClass());
        version.setAuthenticationType(request.authenticationType());
        version.setSecretReference(request.secretReference());
        version.setNetworkPolicy(request.networkPolicy() == null
                ? JsonNodeFactory.instance.objectNode() : request.networkPolicy().deepCopy());
        version.setResponseSizeLimit(request.responseSizeLimit());
        version.setRowVersion(0L);
        version.setCreatedBy(currentOperator());
        version.setCreatedAt(now);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        auditService.success("CONNECTOR_CREATED", "CONNECTOR", endpoint.getId(),
                JsonNodeFactory.instance.objectNode().put("versionId", version.getId()));
        return toResponse(endpoint, version);
    }

    @Transactional
    public ConnectorVersionResponse createDraft(String connectorId, ConnectorVersionMutationRequest request) {
        ConnectorEndpoint endpoint = requireEndpoint(connectorId);
        if (endpoint.getLifecycleState() != AssetLifecycleState.ACTIVE) {
            throw new BizException(40932, "OPENAPI_CONNECTOR_ARCHIVED");
        }
        validate(request);
        targetPolicy.validate(request.baseUrl(), request.networkPolicy());
        if (versionMapper.selectCount(new LambdaQueryWrapper<ConnectorVersion>()
                .eq(ConnectorVersion::getConnectorEndpointId, connectorId)
                .eq(ConnectorVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(40932, "OPENAPI_CONNECTOR_DRAFT_ALREADY_EXISTS");
        }
        ConnectorVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<ConnectorVersion>()
                .eq(ConnectorVersion::getConnectorEndpointId, connectorId)
                .orderByDesc(ConnectorVersion::getVersionNumber)
                .last("LIMIT 1"));
        ConnectorVersion version = new ConnectorVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setConnectorEndpointId(connectorId);
        version.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request);
        LocalDateTime now = LocalDateTime.now();
        version.setRowVersion(0L);
        version.setCreatedBy(currentOperator());
        version.setCreatedAt(now);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        auditService.success("CONNECTOR_DRAFT_CREATED", "CONNECTOR_VERSION", version.getId(),
                JsonNodeFactory.instance.objectNode().put("connectorId", connectorId));
        return toResponse(endpoint, version);
    }

    @Transactional
    public ConnectorVersionResponse updateDraft(String versionId, ConnectorVersionMutationRequest request) {
        validate(request);
        ConnectorVersion version = requireVersion(versionId);
        ConnectorEndpoint endpoint = requireEndpoint(version.getConnectorEndpointId());
        requireDraft(version);
        targetPolicy.validate(request.baseUrl(), request.networkPolicy());
        apply(version, request);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40931, "OPENAPI_CONNECTOR_VERSION_CONFLICT");
        }
        auditService.success("CONNECTOR_DRAFT_UPDATED", "CONNECTOR_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(endpoint, version);
    }

    @Transactional
    public ConnectorVersionResponse publish(String versionId) {
        ConnectorVersion version = requireVersion(versionId);
        ConnectorEndpoint endpoint = requireEndpoint(version.getConnectorEndpointId());
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40931, "OPENAPI_ONLY_DRAFT_CONNECTOR_CAN_BE_PUBLISHED");
        }
        targetPolicy.validate(version.getBaseUrl(), version.getNetworkPolicy());
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(currentOperator());
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40931, "OPENAPI_CONNECTOR_VERSION_CONFLICT");
        }
        auditService.success("CONNECTOR_PUBLISHED", "CONNECTOR_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(endpoint, version);
    }

    @Transactional
    public ConnectorVersionResponse deprecate(String versionId) {
        ConnectorVersion version = requireVersion(versionId);
        ConnectorEndpoint endpoint = requireEndpoint(version.getConnectorEndpointId());
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40931, "OPENAPI_ONLY_PUBLISHED_CONNECTOR_CAN_BE_DEPRECATED");
        }
        version.setLifecycleState(VersionLifecycleState.DEPRECATED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("CONNECTOR_DEPRECATED", "CONNECTOR_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(endpoint, version);
    }

    @Transactional
    public ConnectorVersionResponse archiveVersion(String versionId) {
        ConnectorVersion version = requireVersion(versionId);
        ConnectorEndpoint endpoint = requireEndpoint(version.getConnectorEndpointId());
        if (version.getLifecycleState() == VersionLifecycleState.PUBLISHED) {
            throw new BizException(40931, "OPENAPI_PUBLISHED_CONNECTOR_MUST_BE_DEPRECATED_FIRST");
        }
        version.setLifecycleState(VersionLifecycleState.ARCHIVED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("CONNECTOR_VERSION_ARCHIVED", "CONNECTOR_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(endpoint, version);
    }

    @Transactional
    public void archiveConnector(String connectorId) {
        ConnectorEndpoint endpoint = requireEndpoint(connectorId);
        if (versionMapper.selectCount(new LambdaQueryWrapper<ConnectorVersion>()
                .eq(ConnectorVersion::getConnectorEndpointId, connectorId)
                .in(ConnectorVersion::getLifecycleState,
                        VersionLifecycleState.DRAFT, VersionLifecycleState.PUBLISHED)) > 0) {
            throw new BizException(40932, "OPENAPI_CONNECTOR_HAS_ACTIVE_VERSIONS");
        }
        endpoint.setLifecycleState(AssetLifecycleState.ARCHIVED);
        endpoint.setUpdatedBy(currentOperator());
        endpoint.setUpdatedAt(LocalDateTime.now());
        endpointMapper.updateById(endpoint);
        auditService.success("CONNECTOR_ARCHIVED", "CONNECTOR", connectorId,
                JsonNodeFactory.instance.objectNode());
    }

    public ConnectorVersionResponse getVersion(String versionId) {
        ConnectorVersion version = requireVersion(versionId);
        return toResponse(requireEndpoint(version.getConnectorEndpointId()), version);
    }

    private void validate(CreateConnectorRequest request) {
        if (request == null || !StringUtils.hasText(request.connectorKey())
                || !StringUtils.hasText(request.displayName()) || !StringUtils.hasText(request.ownerId())
                || !StringUtils.hasText(request.baseUrl()) || !StringUtils.hasText(request.operationPath())
                || !request.operationPath().startsWith("/") || request.operationPath().contains("..")
                || !StringUtils.hasText(request.httpMethod())
                || !METHODS.contains(request.httpMethod().toUpperCase(Locale.ROOT))
                || request.timeoutMillis() <= 0 || request.timeoutMillis() > 120_000
                || request.responseSizeLimit() <= 0 || request.responseSizeLimit() > 50L * 1024 * 1024
                || request.operationClass() == null || request.authenticationType() == null
                || (request.authenticationType() != AuthenticationType.NONE
                && !StringUtils.hasText(request.secretReference()))) {
            throw new BizException(40031, "OPENAPI_CONNECTOR_REQUEST_INVALID");
        }
    }

    private void validate(ConnectorVersionMutationRequest request) {
        if (request == null || !StringUtils.hasText(request.baseUrl())
                || !StringUtils.hasText(request.operationPath()) || !request.operationPath().startsWith("/")
                || request.operationPath().contains("..") || !StringUtils.hasText(request.httpMethod())
                || !METHODS.contains(request.httpMethod().toUpperCase(Locale.ROOT))
                || request.timeoutMillis() <= 0 || request.timeoutMillis() > 120_000
                || request.responseSizeLimit() <= 0 || request.responseSizeLimit() > 50L * 1024 * 1024
                || request.operationClass() == null || request.authenticationType() == null
                || (request.authenticationType() != AuthenticationType.NONE
                && !StringUtils.hasText(request.secretReference()))) {
            throw new BizException(40031, "OPENAPI_CONNECTOR_REQUEST_INVALID");
        }
    }

    private void apply(ConnectorVersion version, ConnectorVersionMutationRequest request) {
        version.setBaseUrl(request.baseUrl());
        version.setOperationPath(request.operationPath());
        version.setHttpMethod(request.httpMethod().toUpperCase(Locale.ROOT));
        version.setTimeoutMillis(request.timeoutMillis());
        version.setOperationClass(request.operationClass());
        version.setAuthenticationType(request.authenticationType());
        version.setSecretReference(request.secretReference());
        version.setNetworkPolicy(request.networkPolicy() == null
                ? JsonNodeFactory.instance.objectNode() : request.networkPolicy().deepCopy());
        version.setResponseSizeLimit(request.responseSizeLimit());
    }

    private void requireDraft(ConnectorVersion version) {
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40931, "OPENAPI_PUBLISHED_CONNECTOR_IMMUTABLE");
        }
    }

    private void touch(ConnectorVersion version) {
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
    }

    private ConnectorVersion requireVersion(String id) {
        ConnectorVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(40430, "OPENAPI_CONNECTOR_VERSION_NOT_FOUND");
        }
        return version;
    }

    private ConnectorEndpoint requireEndpoint(String id) {
        ConnectorEndpoint endpoint = endpointMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (endpoint == null || (tenantId != null && !tenantId.equals(endpoint.getTenantId()))) {
            throw new BizException(40430, "OPENAPI_CONNECTOR_NOT_FOUND");
        }
        return endpoint;
    }

    private ConnectorVersionResponse toResponse(ConnectorEndpoint endpoint, ConnectorVersion version) {
        return new ConnectorVersionResponse(
                endpoint.getId(), version.getId(), endpoint.getTenantId(), endpoint.getConnectorKey(),
                endpoint.getDisplayName(), version.getVersionNumber(), version.getLifecycleState(),
                version.getBaseUrl(), version.getOperationPath(), version.getHttpMethod(),
                version.getTimeoutMillis(), version.getOperationClass(), version.getAuthenticationType(),
                version.getSecretReference(), version.getNetworkPolicy(), version.getResponseSizeLimit());
    }

    private String resolveTargetTenant(String requestedTenantId) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            return StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : null;
        }
        if (StringUtils.hasText(requestedTenantId) && !current.equals(requestedTenantId.trim())) {
            throw new BizException(40310, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
