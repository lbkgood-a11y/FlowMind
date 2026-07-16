package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.entity.StructureField;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateStructureRequest;
import com.triobase.service.openapi.dto.StructureResponse;
import com.triobase.service.openapi.infrastructure.mapper.OpenApiStructureMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureFieldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StructureRegistryService {

    private static final int INVALID_REQUEST = 40010;
    private static final int TENANT_ACCESS_DENIED = 40310;
    private static final int STRUCTURE_NOT_FOUND = 40410;
    private static final int STRUCTURE_ALREADY_EXISTS = 40910;

    private final OpenApiStructureMapper structureMapper;
    private final StructureVersionMapper versionMapper;
    private final TenantExtensionValidator tenantExtensionValidator;
    private final StructureSchemaInspector schemaInspector;
    private final StructureFieldMapper fieldMapper;
    private final IntegrationAuditService auditService;

    @Transactional
    public StructureResponse create(CreateStructureRequest request) {
        validateRequest(request);
        List<StructureSchemaInspector.NormalizedField> normalizedFields =
                schemaInspector.inspect(request.getSchemaContent());
        String tenantId = resolveTargetTenant(request.getTenantId());
        StructureVersion parentVersion = validateAndResolveParent(request, tenantId);
        ensureUniqueIdentity(tenantId, request);

        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();

        OpenApiStructure structure = new OpenApiStructure();
        structure.setId(UlidGenerator.nextUlid());
        structure.setTenantId(tenantId);
        structure.setNamespace(request.getNamespace().trim());
        structure.setStructureKey(request.getStructureKey().trim());
        structure.setDisplayName(request.getDisplayName().trim());
        structure.setDescription(request.getDescription());
        structure.setStructureKind(request.getStructureKind());
        structure.setDataFormat("JSON");
        structure.setDirection(request.getDirection());
        structure.setOwnerType(request.getOwnerType().trim());
        structure.setOwnerId(request.getOwnerId().trim());
        structure.setLifecycleState(AssetLifecycleState.ACTIVE);
        structure.setRowVersion(0L);
        structure.setCreatedBy(operator);
        structure.setCreatedAt(now);
        structure.setUpdatedBy(operator);
        structure.setUpdatedAt(now);
        structureMapper.insert(structure);

        StructureVersion draft = new StructureVersion();
        draft.setId(UlidGenerator.nextUlid());
        draft.setStructureId(structure.getId());
        draft.setVersionNumber(1);
        draft.setCompatibilityLine(1);
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        draft.setSchemaContent(request.getSchemaContent().deepCopy());
        draft.setSchemaHash(sha256(request.getSchemaContent().toString()));
        draft.setParentStructureVersionId(parentVersion == null ? null : parentVersion.getId());
        draft.setChangeSummary(request.getChangeSummary());
        draft.setSemanticChange(JsonNodeFactory.instance.objectNode());
        draft.setCompatibilityResult(JsonNodeFactory.instance.objectNode());
        draft.setRowVersion(0L);
        draft.setCreatedBy(operator);
        draft.setCreatedAt(now);
        draft.setUpdatedBy(operator);
        draft.setUpdatedAt(now);
        versionMapper.insert(draft);
        persistFields(draft.getId(), normalizedFields, now);
        auditService.success("STRUCTURE_CREATED", "STRUCTURE", structure.getId(),
                JsonNodeFactory.instance.objectNode()
                        .put("versionId", draft.getId())
                        .put("namespace", structure.getNamespace())
                        .put("structureKey", structure.getStructureKey()));

        return toResponse(structure, draft);
    }

    public StructureResponse getById(String structureId) {
        OpenApiStructure structure = structureMapper.selectById(structureId);
        if (structure == null) {
            throw new BizException(STRUCTURE_NOT_FOUND, "OPENAPI_STRUCTURE_NOT_FOUND");
        }
        assertTenantAccess(structure.getTenantId());
        return toResponse(structure, findLatestVersion(structureId));
    }

    public PageResult<StructureResponse> list(
            int pageNumber,
            int pageSize,
            String namespace,
            String structureKind) {
        int safePage = Math.max(pageNumber, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        LambdaQueryWrapper<OpenApiStructure> query = new LambdaQueryWrapper<OpenApiStructure>()
                .eq(StringUtils.hasText(namespace), OpenApiStructure::getNamespace, namespace)
                .eq(StringUtils.hasText(structureKind), OpenApiStructure::getStructureKind, structureKind)
                .orderByDesc(OpenApiStructure::getUpdatedAt);
        Page<OpenApiStructure> page = structureMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<StructureResponse> records = page.getRecords().stream()
                .map(structure -> toResponse(structure, findLatestVersion(structure.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), safePage, safeSize);
    }

    private void validateRequest(CreateStructureRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getNamespace())
                || !StringUtils.hasText(request.getStructureKey())
                || !StringUtils.hasText(request.getDisplayName())
                || request.getStructureKind() == null
                || request.getDirection() == null
                || !StringUtils.hasText(request.getOwnerType())
                || !StringUtils.hasText(request.getOwnerId())
                || request.getSchemaContent() == null
                || !request.getSchemaContent().isObject()) {
            throw new BizException(INVALID_REQUEST, "OPENAPI_STRUCTURE_REQUEST_INVALID");
        }
    }

    private String resolveTargetTenant(String requestedTenantId) {
        String currentTenantId = SecurityContextHolder.getTenantId();
        if (currentTenantId == null) {
            return StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : null;
        }
        if (StringUtils.hasText(requestedTenantId) && !currentTenantId.equals(requestedTenantId.trim())) {
            throw new BizException(TENANT_ACCESS_DENIED, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return currentTenantId;
    }

    private StructureVersion validateAndResolveParent(CreateStructureRequest request, String tenantId) {
        if (request.getStructureKind() != StructureKind.TENANT_EXTENSION) {
            if (StringUtils.hasText(request.getParentStructureVersionId())) {
                throw new BizException(INVALID_REQUEST, "OPENAPI_PARENT_ONLY_ALLOWED_FOR_TENANT_EXTENSION");
            }
            return null;
        }
        if (tenantId == null || !StringUtils.hasText(request.getParentStructureVersionId())) {
            throw new BizException(INVALID_REQUEST, "OPENAPI_TENANT_EXTENSION_PARENT_REQUIRED");
        }
        StructureVersion parentVersion = versionMapper.selectById(request.getParentStructureVersionId());
        if (parentVersion == null || parentVersion.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(INVALID_REQUEST, "OPENAPI_TENANT_EXTENSION_PARENT_MUST_BE_PUBLISHED");
        }
        OpenApiStructure parent = structureMapper.selectById(parentVersion.getStructureId());
        if (parent == null || parent.getStructureKind() != StructureKind.CANONICAL) {
            throw new BizException(INVALID_REQUEST, "OPENAPI_TENANT_EXTENSION_PARENT_MUST_BE_CANONICAL");
        }
        tenantExtensionValidator.validate(parentVersion.getSchemaContent(), request.getSchemaContent(), tenantId);
        return parentVersion;
    }

    private void ensureUniqueIdentity(String tenantId, CreateStructureRequest request) {
        LambdaQueryWrapper<OpenApiStructure> query = new LambdaQueryWrapper<OpenApiStructure>()
                .eq(OpenApiStructure::getNamespace, request.getNamespace().trim())
                .eq(OpenApiStructure::getStructureKey, request.getStructureKey().trim())
                .eq(OpenApiStructure::getStructureKind, request.getStructureKind());
        if (tenantId == null) {
            query.isNull(OpenApiStructure::getTenantId);
        } else {
            query.eq(OpenApiStructure::getTenantId, tenantId);
        }
        if (structureMapper.selectCount(query) > 0) {
            throw new BizException(STRUCTURE_ALREADY_EXISTS, "OPENAPI_STRUCTURE_ALREADY_EXISTS");
        }
    }

    private void assertTenantAccess(String resourceTenantId) {
        String currentTenantId = SecurityContextHolder.getTenantId();
        if (currentTenantId != null && !currentTenantId.equals(resourceTenantId)) {
            throw new BizException(STRUCTURE_NOT_FOUND, "OPENAPI_STRUCTURE_NOT_FOUND");
        }
    }

    private StructureVersion findLatestVersion(String structureId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<StructureVersion>()
                .eq(StructureVersion::getStructureId, structureId)
                .orderByDesc(StructureVersion::getVersionNumber)
                .last("LIMIT 1"));
    }

    private void persistFields(
            String structureVersionId,
            List<StructureSchemaInspector.NormalizedField> normalizedFields,
            LocalDateTime createdAt) {
        for (StructureSchemaInspector.NormalizedField normalized : normalizedFields) {
            StructureField field = new StructureField();
            field.setId(UlidGenerator.nextUlid());
            field.setStructureVersionId(structureVersionId);
            field.setJsonPointer(normalized.jsonPointer());
            field.setFieldName(normalized.fieldName());
            field.setDataType(normalized.dataType());
            field.setRequiredField(normalized.required());
            field.setArrayField(normalized.array());
            field.setSemanticId(normalized.semanticId());
            field.setSensitivityLevel(normalized.sensitivity());
            field.setFieldConstraints(normalized.constraints());
            field.setOrdinal(normalized.ordinal());
            field.setCreatedAt(createdAt);
            fieldMapper.insert(field);
        }
    }

    private StructureResponse toResponse(OpenApiStructure structure, StructureVersion version) {
        return new StructureResponse(
                structure.getId(), structure.getTenantId(), structure.getNamespace(),
                structure.getStructureKey(), structure.getDisplayName(), structure.getDescription(),
                structure.getStructureKind(), structure.getDirection(), structure.getOwnerType(),
                structure.getOwnerId(), structure.getLifecycleState(),
                version == null ? null : version.getVersionNumber(),
                version == null ? null : version.getId(),
                version == null ? null : version.getLifecycleState(),
                structure.getCreatedAt(), structure.getUpdatedAt());
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
