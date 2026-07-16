package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.OpenApiExportRequest;
import com.triobase.service.openapi.infrastructure.mapper.OpenApiStructureMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OpenApiExportService {

    private static final int INVALID_EXPORT = 40014;
    private static final int VERSION_NOT_FOUND = 40411;
    private static final int VERSION_NOT_PUBLISHED = 40918;
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head");

    private final StructureVersionMapper versionMapper;
    private final OpenApiStructureMapper structureMapper;
    private final ObjectMapper objectMapper;
    private final IntegrationAuditService auditService;

    public JsonNode export(OpenApiExportRequest request) {
        validateRequest(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.1.0");
        ObjectNode info = root.putObject("info");
        info.put("title", request.title());
        info.put("version", request.apiVersion());
        if (StringUtils.hasText(request.serverUrl())) {
            root.putArray("servers").addObject().put("url", request.serverUrl());
        }
        ObjectNode paths = root.putObject("paths");
        ObjectNode schemas = root.putObject("components").putObject("schemas");
        Map<String, String> componentNames = new HashMap<>();

        for (OpenApiExportRequest.ExportOperation operation : request.operations()) {
            ObjectNode pathItem = paths.has(operation.path())
                    ? (ObjectNode) paths.get(operation.path())
                    : paths.putObject(operation.path());
            ObjectNode operationNode = pathItem.putObject(operation.method().toLowerCase(Locale.ROOT));
            operationNode.put("operationId", operation.operationId());
            if (StringUtils.hasText(operation.requestStructureVersionId())) {
                String component = componentFor(operation.requestStructureVersionId(), schemas, componentNames);
                operationNode.putObject("requestBody")
                        .put("required", true)
                        .putObject("content")
                        .putObject("application/json")
                        .putObject("schema")
                        .put("$ref", "#/components/schemas/" + component);
            }
            ObjectNode response = operationNode.putObject("responses").putObject("200");
            response.put("description", "Successful response");
            if (StringUtils.hasText(operation.responseStructureVersionId())) {
                String component = componentFor(operation.responseStructureVersionId(), schemas, componentNames);
                response.putObject("content")
                        .putObject("application/json")
                        .putObject("schema")
                        .put("$ref", "#/components/schemas/" + component);
            }
        }
        root.put("x-triobase-exported", true);
        auditService.success("OPENAPI_EXPORTED", "OPENAPI_DOCUMENT", request.apiVersion(),
                objectMapper.createObjectNode().put("operationCount", request.operations().size()));
        return root;
    }

    private String componentFor(
            String versionId,
            ObjectNode schemas,
            Map<String, String> componentNames) {
        if (componentNames.containsKey(versionId)) {
            return componentNames.get(versionId);
        }
        StructureVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_STRUCTURE_VERSION_NOT_FOUND");
        }
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(VERSION_NOT_PUBLISHED, "OPENAPI_EXPORT_REQUIRES_PUBLISHED_STRUCTURE");
        }
        OpenApiStructure structure = structureMapper.selectById(version.getStructureId());
        if (structure == null) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_STRUCTURE_NOT_FOUND");
        }
        String tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null && !tenantId.equals(structure.getTenantId())) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_STRUCTURE_NOT_FOUND");
        }
        String componentName = normalizeComponent(
                structure.getNamespace() + "_" + structure.getStructureKey() + "_v" + version.getVersionNumber());
        ObjectNode schema = version.getSchemaContent().deepCopy();
        schema.put("x-triobase-structure-id", structure.getId());
        schema.put("x-triobase-structure-version-id", version.getId());
        schema.put("x-triobase-lifecycle", version.getLifecycleState().name());
        schema.put("x-triobase-compatibility-line", version.getCompatibilityLine());
        schemas.set(componentName, schema);
        componentNames.put(versionId, componentName);
        return componentName;
    }

    private void validateRequest(OpenApiExportRequest request) {
        if (request == null || !StringUtils.hasText(request.title())
                || !StringUtils.hasText(request.apiVersion())
                || request.operations() == null || request.operations().isEmpty()) {
            throw new BizException(INVALID_EXPORT, "OPENAPI_EXPORT_REQUEST_INVALID");
        }
        for (OpenApiExportRequest.ExportOperation operation : request.operations()) {
            if (operation == null || !StringUtils.hasText(operation.path())
                    || !operation.path().startsWith("/")
                    || !StringUtils.hasText(operation.method())
                    || !METHODS.contains(operation.method().toLowerCase(Locale.ROOT))
                    || !StringUtils.hasText(operation.operationId())) {
                throw new BizException(INVALID_EXPORT, "OPENAPI_EXPORT_OPERATION_INVALID");
            }
        }
    }

    private String normalizeComponent(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(normalized.charAt(0)) ? "S_" + normalized : normalized;
    }
}
