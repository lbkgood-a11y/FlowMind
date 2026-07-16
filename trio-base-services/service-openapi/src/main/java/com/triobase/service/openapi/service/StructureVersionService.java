package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.entity.StructureField;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.StructureVersionResponse;
import com.triobase.service.openapi.dto.PublicationApproval;
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
public class StructureVersionService {

    private static final int INVALID_SCHEMA = 40011;
    private static final int TENANT_ACCESS_DENIED = 40310;
    private static final int STRUCTURE_NOT_FOUND = 40410;
    private static final int VERSION_NOT_FOUND = 40411;
    private static final int DRAFT_ALREADY_EXISTS = 40911;
    private static final int IMMUTABLE_VERSION = 40912;
    private static final int INVALID_TRANSITION = 40913;
    private static final int OPTIMISTIC_CONFLICT = 40914;
    private static final int APPROVAL_REQUIRED = 40916;
    private static final int NEW_COMPATIBILITY_LINE_REQUIRED = 40917;

    private final OpenApiStructureMapper structureMapper;
    private final StructureVersionMapper versionMapper;
    private final StructureFieldMapper fieldMapper;
    private final StructureSchemaInspector schemaInspector;
    private final SchemaCompatibilityAnalyzer compatibilityAnalyzer;
    private final IntegrationAuditService auditService;

    @Transactional
    public StructureVersionResponse createDraft(
            String structureId,
            JsonNode schemaContent,
            String changeSummary) {
        requireObjectSchema(schemaContent);
        List<StructureSchemaInspector.NormalizedField> normalizedFields = schemaInspector.inspect(schemaContent);
        requireStructure(structureId);
        long existingDrafts = versionMapper.selectCount(new LambdaQueryWrapper<StructureVersion>()
                .eq(StructureVersion::getStructureId, structureId)
                .eq(StructureVersion::getLifecycleState, VersionLifecycleState.DRAFT));
        if (existingDrafts > 0) {
            throw new BizException(DRAFT_ALREADY_EXISTS, "OPENAPI_STRUCTURE_DRAFT_ALREADY_EXISTS");
        }
        StructureVersion latest = findLatest(structureId);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        StructureVersion draft = new StructureVersion();
        draft.setId(UlidGenerator.nextUlid());
        draft.setStructureId(structureId);
        draft.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        draft.setCompatibilityLine(latest == null || latest.getCompatibilityLine() == null
                ? 1 : latest.getCompatibilityLine());
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        draft.setSchemaContent(schemaContent.deepCopy());
        draft.setSchemaHash(sha256(schemaContent.toString()));
        draft.setParentStructureVersionId(null);
        draft.setChangeSummary(changeSummary);
        draft.setSemanticChange(JsonNodeFactory.instance.objectNode());
        draft.setCompatibilityResult(JsonNodeFactory.instance.objectNode());
        draft.setRowVersion(0L);
        draft.setCreatedBy(operator);
        draft.setCreatedAt(now);
        draft.setUpdatedBy(operator);
        draft.setUpdatedAt(now);
        versionMapper.insert(draft);
        persistFields(draft.getId(), normalizedFields, now);
        auditService.success("STRUCTURE_DRAFT_CREATED", "STRUCTURE_VERSION", draft.getId(),
                JsonNodeFactory.instance.objectNode().put("structureId", structureId));
        return toResponse(draft);
    }

    @Transactional
    public StructureVersionResponse updateDraft(
            String versionId,
            JsonNode schemaContent,
            String changeSummary,
            JsonNode semanticChange) {
        requireObjectSchema(schemaContent);
        List<StructureSchemaInspector.NormalizedField> normalizedFields = schemaInspector.inspect(schemaContent);
        StructureVersion version = requireVersion(versionId);
        requireStructure(version.getStructureId());
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_PUBLISHED_STRUCTURE_VERSION_IMMUTABLE");
        }
        version.setSchemaContent(schemaContent.deepCopy());
        version.setSchemaHash(sha256(schemaContent.toString()));
        version.setChangeSummary(changeSummary);
        version.setSemanticChange(semanticChange == null
                ? JsonNodeFactory.instance.objectNode() : semanticChange.deepCopy());
        ensureUpdated(version);
        fieldMapper.delete(new LambdaQueryWrapper<StructureField>()
                .eq(StructureField::getStructureVersionId, versionId));
        persistFields(versionId, normalizedFields, LocalDateTime.now());
        auditService.success("STRUCTURE_DRAFT_UPDATED", "STRUCTURE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode().put("schemaHash", version.getSchemaHash()));
        return toResponse(version);
    }

    @Transactional
    public StructureVersionResponse publish(String versionId) {
        return publish(versionId, PublicationApproval.none());
    }

    @Transactional
    public StructureVersionResponse publish(String versionId, PublicationApproval approval) {
        StructureVersion version = requireVersion(versionId);
        requireStructure(version.getStructureId());
        requireState(version, VersionLifecycleState.DRAFT, "OPENAPI_ONLY_DRAFT_CAN_BE_PUBLISHED");
        schemaInspector.inspect(version.getSchemaContent());
        StructureVersion previous = findLatestPublished(version.getStructureId(), version.getId());
        if (previous == null) {
            version.setCompatibilityLine(1);
            version.setCompatibilityResult(JsonNodeFactory.instance.objectNode()
                    .put("compatible", true)
                    .put("initialVersion", true));
        } else {
            SchemaCompatibilityAnalyzer.CompatibilityReport report = compatibilityAnalyzer.analyze(
                    previous.getSchemaContent(), version.getSchemaContent(), version.getSemanticChange());
            version.setCompatibilityResult(report.toJson());
            boolean reviewRequired = report.breaking()
                    || report.securitySensitive()
                    || report.semanticReviewRequired();
            if (reviewRequired && (approval == null || !approval.dualApproved())) {
                throw new BizException(APPROVAL_REQUIRED, "OPENAPI_STRUCTURE_PUBLICATION_DUAL_APPROVAL_REQUIRED");
            }
            if (report.breaking()) {
                if (approval == null || !approval.startNewCompatibilityLine()) {
                    throw new BizException(
                            NEW_COMPATIBILITY_LINE_REQUIRED,
                            "OPENAPI_BREAKING_CHANGE_REQUIRES_NEW_COMPATIBILITY_LINE");
                }
                version.setCompatibilityLine(previous.getCompatibilityLine() + 1);
            } else {
                version.setCompatibilityLine(previous.getCompatibilityLine());
            }
        }
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(currentOperator());
        version.setPublishedAt(LocalDateTime.now());
        ensureUpdated(version);
        auditService.success("STRUCTURE_VERSION_PUBLISHED", "STRUCTURE_VERSION", versionId,
                version.getCompatibilityResult());
        return toResponse(version);
    }

    @Transactional
    public StructureVersionResponse deprecate(String versionId) {
        StructureVersion version = requireVersion(versionId);
        requireStructure(version.getStructureId());
        requireState(version, VersionLifecycleState.PUBLISHED, "OPENAPI_ONLY_PUBLISHED_CAN_BE_DEPRECATED");
        version.setLifecycleState(VersionLifecycleState.DEPRECATED);
        version.setDeprecatedAt(LocalDateTime.now());
        ensureUpdated(version);
        auditService.success("STRUCTURE_VERSION_DEPRECATED", "STRUCTURE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(version);
    }

    @Transactional
    public StructureVersionResponse archive(String versionId) {
        StructureVersion version = requireVersion(versionId);
        requireStructure(version.getStructureId());
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED
                && version.getLifecycleState() != VersionLifecycleState.DEPRECATED) {
            throw new BizException(INVALID_TRANSITION, "OPENAPI_ONLY_RELEASED_VERSION_CAN_BE_ARCHIVED");
        }
        version.setLifecycleState(VersionLifecycleState.ARCHIVED);
        version.setArchivedAt(LocalDateTime.now());
        ensureUpdated(version);
        auditService.success("STRUCTURE_VERSION_ARCHIVED", "STRUCTURE_VERSION", versionId,
                JsonNodeFactory.instance.objectNode());
        return toResponse(version);
    }

    public StructureVersionResponse getById(String versionId) {
        StructureVersion version = requireVersion(versionId);
        requireStructure(version.getStructureId());
        return toResponse(version);
    }

    private OpenApiStructure requireStructure(String structureId) {
        OpenApiStructure structure = structureMapper.selectById(structureId);
        if (structure == null) {
            throw new BizException(STRUCTURE_NOT_FOUND, "OPENAPI_STRUCTURE_NOT_FOUND");
        }
        String tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null && !tenantId.equals(structure.getTenantId())) {
            throw new BizException(TENANT_ACCESS_DENIED, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return structure;
    }

    private StructureVersion requireVersion(String versionId) {
        StructureVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_STRUCTURE_VERSION_NOT_FOUND");
        }
        return version;
    }

    private StructureVersion findLatest(String structureId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<StructureVersion>()
                .eq(StructureVersion::getStructureId, structureId)
                .orderByDesc(StructureVersion::getVersionNumber)
                .last("LIMIT 1"));
    }

    private StructureVersion findLatestPublished(String structureId, String excludedVersionId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<StructureVersion>()
                .eq(StructureVersion::getStructureId, structureId)
                .eq(StructureVersion::getLifecycleState, VersionLifecycleState.PUBLISHED)
                .ne(StructureVersion::getId, excludedVersionId)
                .orderByDesc(StructureVersion::getVersionNumber)
                .last("LIMIT 1"));
    }

    private void requireState(StructureVersion version, VersionLifecycleState state, String message) {
        if (version.getLifecycleState() != state) {
            throw new BizException(INVALID_TRANSITION, message);
        }
    }

    private void requireObjectSchema(JsonNode schemaContent) {
        if (schemaContent == null || !schemaContent.isObject()) {
            throw new BizException(INVALID_SCHEMA, "OPENAPI_STRUCTURE_SCHEMA_INVALID");
        }
    }

    private void ensureUpdated(StructureVersion version) {
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(OPTIMISTIC_CONFLICT, "OPENAPI_STRUCTURE_VERSION_CONFLICT");
        }
    }

    private StructureVersionResponse toResponse(StructureVersion version) {
        return new StructureVersionResponse(
                version.getId(), version.getStructureId(), version.getVersionNumber(), version.getCompatibilityLine(),
                version.getLifecycleState(), version.getSchemaContent(), version.getSchemaHash(),
                version.getParentStructureVersionId(), version.getChangeSummary(),
                version.getSemanticChange(), version.getCompatibilityResult(), version.getPublishedBy(),
                version.getPublishedAt(), version.getDeprecatedAt(), version.getArchivedAt());
    }

    private void persistFields(
            String structureVersionId,
            List<StructureSchemaInspector.NormalizedField> fields,
            LocalDateTime createdAt) {
        for (StructureSchemaInspector.NormalizedField normalized : fields) {
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
