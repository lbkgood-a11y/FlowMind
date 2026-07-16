package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.StructureProvenance;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.dto.CreateStructureRequest;
import com.triobase.service.openapi.dto.ImportOpenApiRequest;
import com.triobase.service.openapi.dto.OpenApiImportResult;
import com.triobase.service.openapi.dto.StructureResponse;
import com.triobase.service.openapi.infrastructure.mapper.StructureProvenanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OpenApiImportService {

    private static final int INVALID_OPENAPI = 40013;
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    private final StructureRegistryService structureRegistryService;
    private final StructureProvenanceMapper provenanceMapper;
    private final ObjectMapper jsonMapper;
    private final IntegrationAuditService auditService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Transactional
    public OpenApiImportResult importAll(ImportOpenApiRequest request) {
        validateRequest(request);
        JsonNode document = parse(request.document());
        String openApiVersion = document.path("openapi").asText();
        if (!openApiVersion.startsWith("3.")) {
            throw new BizException(INVALID_OPENAPI, "OPENAPI_3_DOCUMENT_REQUIRED");
        }
        List<SchemaCandidate> candidates = discoverSchemas(document);
        if (candidates.isEmpty()) {
            throw new BizException(INVALID_OPENAPI, "OPENAPI_DOCUMENT_HAS_NO_IMPORTABLE_SCHEMAS");
        }
        String sourceHash = sha256(request.document());
        List<StructureResponse> imported = new ArrayList<>();
        for (SchemaCandidate candidate : candidates) {
            JsonNode resolvedSchema = resolveReferences(candidate.schema(), document, new HashSet<>());
            CreateStructureRequest create = new CreateStructureRequest();
            create.setTenantId(request.tenantId());
            create.setNamespace(request.namespace());
            create.setStructureKey(candidate.structureKey());
            create.setDisplayName(candidate.displayName());
            create.setDescription("Imported from " + request.sourceName() + " at " + candidate.location());
            create.setStructureKind(request.structureKind());
            create.setDirection(candidate.direction());
            create.setOwnerType(request.ownerType());
            create.setOwnerId(request.ownerId());
            create.setSchemaContent(resolvedSchema);
            create.setChangeSummary("Initial OpenAPI import");
            StructureResponse response = structureRegistryService.create(create);
            insertProvenance(response, request, candidate, sourceHash, openApiVersion);
            imported.add(response);
        }
        auditService.success("OPENAPI_IMPORTED", "OPENAPI_DOCUMENT", sourceHash,
                jsonMapper.createObjectNode().put("structureCount", imported.size()));
        return new OpenApiImportResult(openApiVersion, sourceHash, List.copyOf(imported));
    }

    private List<SchemaCandidate> discoverSchemas(JsonNode document) {
        List<SchemaCandidate> candidates = new ArrayList<>();
        JsonNode schemas = document.path("components").path("schemas");
        schemas.fields().forEachRemaining(entry -> candidates.add(new SchemaCandidate(
                normalizeKey(entry.getKey()), entry.getKey(), StructureDirection.BIDIRECTIONAL,
                entry.getValue(), "#/components/schemas/" + entry.getKey())));

        Iterator<Map.Entry<String, JsonNode>> paths = document.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (!HTTP_METHODS.contains(operation.getKey().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String operationKey = operation.getValue().path("operationId").asText(
                        normalizeKey(operation.getKey() + "-" + path.getKey()));
                JsonNode requestSchema = contentSchema(operation.getValue().path("requestBody"));
                if (requestSchema != null) {
                    candidates.add(new SchemaCandidate(
                            normalizeKey(operationKey + "-request"), operationKey + " request",
                            StructureDirection.REQUEST, requestSchema,
                            "#/paths/" + path.getKey() + "/" + operation.getKey() + "/requestBody"));
                }
                Iterator<Map.Entry<String, JsonNode>> responses = operation.getValue().path("responses").fields();
                while (responses.hasNext()) {
                    Map.Entry<String, JsonNode> response = responses.next();
                    JsonNode responseSchema = contentSchema(response.getValue());
                    if (responseSchema != null) {
                        candidates.add(new SchemaCandidate(
                                normalizeKey(operationKey + "-response-" + response.getKey()),
                                operationKey + " response " + response.getKey(),
                                StructureDirection.RESPONSE, responseSchema,
                                "#/paths/" + path.getKey() + "/" + operation.getKey()
                                        + "/responses/" + response.getKey()));
                    }
                }
            }
        }
        return candidates;
    }

    private JsonNode contentSchema(JsonNode container) {
        JsonNode content = container.path("content");
        if (!content.isObject()) {
            return null;
        }
        JsonNode json = content.path("application/json").path("schema");
        if (!json.isMissingNode()) {
            return json;
        }
        Iterator<JsonNode> mediaTypes = content.elements();
        while (mediaTypes.hasNext()) {
            JsonNode schema = mediaTypes.next().path("schema");
            if (!schema.isMissingNode()) {
                return schema;
            }
        }
        return null;
    }

    private JsonNode resolveReferences(JsonNode node, JsonNode document, Set<String> stack) {
        if (node.isObject() && node.has("$ref")) {
            String reference = node.path("$ref").asText();
            if (!reference.startsWith("#/")) {
                throw new BizException(INVALID_OPENAPI, "OPENAPI_EXTERNAL_REFERENCE_UNSUPPORTED:" + reference);
            }
            if (!stack.add(reference)) {
                throw new BizException(INVALID_OPENAPI, "OPENAPI_CYCLIC_REFERENCE_UNSUPPORTED:" + reference);
            }
            JsonNode referenced = document.at(reference.substring(1));
            if (referenced.isMissingNode()) {
                throw new BizException(INVALID_OPENAPI, "OPENAPI_REFERENCE_UNRESOLVED:" + reference);
            }
            JsonNode resolved = resolveReferences(referenced, document, stack);
            stack.remove(reference);
            return resolved;
        }
        if (node.isObject()) {
            ObjectNode result = jsonMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                    result.set(entry.getKey(), resolveReferences(entry.getValue(), document, stack)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = jsonMapper.createArrayNode();
            node.forEach(item -> result.add(resolveReferences(item, document, stack)));
            return result;
        }
        return node.deepCopy();
    }

    private JsonNode parse(byte[] content) {
        try {
            String text = new String(content, StandardCharsets.UTF_8).stripLeading();
            return text.startsWith("{") ? jsonMapper.readTree(content) : yamlMapper.readTree(content);
        } catch (Exception exception) {
            throw new BizException(INVALID_OPENAPI, "OPENAPI_DOCUMENT_PARSE_FAILED:" + exception.getMessage());
        }
    }

    private void insertProvenance(
            StructureResponse response,
            ImportOpenApiRequest request,
            SchemaCandidate candidate,
            String sourceHash,
            String openApiVersion) {
        StructureProvenance provenance = new StructureProvenance();
        provenance.setId(UlidGenerator.nextUlid());
        provenance.setStructureVersionId(response.latestVersionId());
        provenance.setSourceType("OPENAPI_IMPORT");
        provenance.setSourceName(request.sourceName());
        provenance.setSourceLocation(candidate.location());
        provenance.setDocumentHash(sourceHash);
        provenance.setImportedOperation(candidate.structureKey());
        ObjectNode metadata = jsonMapper.createObjectNode();
        metadata.put("openapiVersion", openApiVersion);
        metadata.put("direction", candidate.direction().name());
        provenance.setMetadata(metadata);
        provenance.setCreatedBy(currentOperator());
        provenance.setCreatedAt(LocalDateTime.now());
        provenanceMapper.insert(provenance);
    }

    private void validateRequest(ImportOpenApiRequest request) {
        if (request == null || request.document() == null || request.document().length == 0
                || !StringUtils.hasText(request.sourceName())
                || !StringUtils.hasText(request.namespace())
                || request.structureKind() == null
                || request.structureKind() == StructureKind.TENANT_EXTENSION
                || !StringUtils.hasText(request.ownerType())
                || !StringUtils.hasText(request.ownerId())) {
            throw new BizException(INVALID_OPENAPI, "OPENAPI_IMPORT_REQUEST_INVALID");
        }
    }

    private String normalizeKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "schema" : normalized;
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record SchemaCandidate(
            String structureKey,
            String displayName,
            StructureDirection direction,
            JsonNode schema,
            String location) {
    }
}
