package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.OrchestrationDefinition;
import com.triobase.service.openapi.domain.entity.OrchestrationVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateOrchestrationRequest;
import com.triobase.service.openapi.dto.OrchestrationVersionMutationRequest;
import com.triobase.service.openapi.dto.OrchestrationVersionResponse;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OrchestrationRegistryService {

    private final OrchestrationDefinitionMapper definitionMapper;
    private final OrchestrationVersionMapper versionMapper;
    private final OrchestrationDefinitionValidator validator;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrchestrationVersionResponse create(CreateOrchestrationRequest request) {
        String tenantId = targetTenant(request.tenantId());
        String key = request.orchestrationKey().trim();
        if (definitionMapper.selectCount(new LambdaQueryWrapper<OrchestrationDefinition>()
                .eq(OrchestrationDefinition::getOrchestrationKey, key)
                .eq(tenantId != null, OrchestrationDefinition::getTenantId, tenantId)
                .isNull(tenantId == null, OrchestrationDefinition::getTenantId)) > 0) {
            throw new BizException(40950, "OPENAPI_ORCHESTRATION_ALREADY_EXISTS");
        }
        OrchestrationDefinitionValidator.ValidationResult validation =
                validator.validate(request.schemaVersion(), request.definitionContent());
        OrchestrationDefinition definition = new OrchestrationDefinition();
        LocalDateTime now = LocalDateTime.now();
        definition.setId(UlidGenerator.nextUlid());
        definition.setTenantId(tenantId);
        definition.setOrchestrationKey(key);
        definition.setDisplayName(request.displayName().trim());
        definition.setOwnerId(request.ownerId().trim());
        definition.setLifecycleState(AssetLifecycleState.ACTIVE);
        initialize(definition, now);
        definitionMapper.insert(definition);

        OrchestrationVersion version = new OrchestrationVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setOrchestrationDefinitionId(definition.getId());
        version.setVersionNumber(1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request.schemaVersion(), request.definitionContent(), validation);
        initialize(version, now);
        versionMapper.insert(version);
        auditService.success("ORCHESTRATION_CREATED", "ORCHESTRATION", definition.getId(),
                validator.validationJson(validation));
        return response(definition, version);
    }

    @Transactional
    public OrchestrationVersionResponse createDraft(
            String orchestrationId, OrchestrationVersionMutationRequest request) {
        OrchestrationDefinition definition = requireDefinition(orchestrationId);
        if (versionMapper.selectCount(new LambdaQueryWrapper<OrchestrationVersion>()
                .eq(OrchestrationVersion::getOrchestrationDefinitionId, orchestrationId)
                .eq(OrchestrationVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(40950, "OPENAPI_ORCHESTRATION_DRAFT_ALREADY_EXISTS");
        }
        OrchestrationVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<OrchestrationVersion>()
                .eq(OrchestrationVersion::getOrchestrationDefinitionId, orchestrationId)
                .orderByDesc(OrchestrationVersion::getVersionNumber)
                .last("LIMIT 1"));
        OrchestrationDefinitionValidator.ValidationResult validation =
                validator.validate(request.schemaVersion(), request.definitionContent());
        OrchestrationVersion version = new OrchestrationVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setOrchestrationDefinitionId(orchestrationId);
        version.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request.schemaVersion(), request.definitionContent(), validation);
        initialize(version, LocalDateTime.now());
        versionMapper.insert(version);
        auditService.success("ORCHESTRATION_DRAFT_CREATED", "ORCHESTRATION_VERSION", version.getId(),
                validator.validationJson(validation));
        return response(definition, version);
    }

    @Transactional
    public OrchestrationVersionResponse updateDraft(
            String versionId, OrchestrationVersionMutationRequest request) {
        OrchestrationVersion version = requireVersion(versionId);
        OrchestrationDefinition definition = requireDefinition(version.getOrchestrationDefinitionId());
        requireDraft(version);
        OrchestrationDefinitionValidator.ValidationResult validation =
                validator.validate(request.schemaVersion(), request.definitionContent());
        apply(version, request.schemaVersion(), request.definitionContent(), validation);
        touch(version);
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40951, "OPENAPI_ORCHESTRATION_VERSION_CONFLICT");
        }
        auditService.success("ORCHESTRATION_DRAFT_UPDATED", "ORCHESTRATION_VERSION", versionId,
                validator.validationJson(validation));
        return response(definition, version);
    }

    @Transactional
    public OrchestrationVersionResponse publish(String versionId) {
        OrchestrationVersion version = requireVersion(versionId);
        OrchestrationDefinition definition = requireDefinition(version.getOrchestrationDefinitionId());
        requireDraft(version);
        OrchestrationDefinitionValidator.ValidationResult validation =
                validator.validate(version.getDefinitionSchemaVersion(), version.getDefinitionContent());
        if (!validation.valid()) {
            throw new BizException(42250,
                    "OPENAPI_ORCHESTRATION_INVALID:" + String.join(",", validation.errors()));
        }
        version.setValidationResult(validator.validationJson(validation));
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(operator());
        version.setPublishedAt(LocalDateTime.now());
        touch(version);
        if (versionMapper.updateById(version) != 1) {
            throw new BizException(40951, "OPENAPI_ORCHESTRATION_VERSION_CONFLICT");
        }
        auditService.success("ORCHESTRATION_PUBLISHED", "ORCHESTRATION_VERSION", versionId,
                version.getValidationResult());
        return response(definition, version);
    }

    @Transactional
    public OrchestrationVersionResponse deprecate(String versionId) {
        OrchestrationVersion version = requireVersion(versionId);
        OrchestrationDefinition definition = requireDefinition(version.getOrchestrationDefinitionId());
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40951, "OPENAPI_ONLY_PUBLISHED_ORCHESTRATION_CAN_BE_DEPRECATED");
        }
        version.setLifecycleState(VersionLifecycleState.DEPRECATED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("ORCHESTRATION_DEPRECATED", "ORCHESTRATION_VERSION", versionId,
                objectMapper.createObjectNode());
        return response(definition, version);
    }

    @Transactional
    public OrchestrationVersionResponse archiveVersion(String versionId) {
        OrchestrationVersion version = requireVersion(versionId);
        OrchestrationDefinition definition = requireDefinition(version.getOrchestrationDefinitionId());
        if (version.getLifecycleState() == VersionLifecycleState.PUBLISHED) {
            throw new BizException(40951, "OPENAPI_PUBLISHED_ORCHESTRATION_MUST_BE_DEPRECATED_FIRST");
        }
        version.setLifecycleState(VersionLifecycleState.ARCHIVED);
        touch(version);
        versionMapper.updateById(version);
        auditService.success("ORCHESTRATION_VERSION_ARCHIVED", "ORCHESTRATION_VERSION", versionId,
                objectMapper.createObjectNode());
        return response(definition, version);
    }

    @Transactional
    public void archiveDefinition(String orchestrationId) {
        OrchestrationDefinition definition = requireDefinition(orchestrationId);
        if (versionMapper.selectCount(new LambdaQueryWrapper<OrchestrationVersion>()
                .eq(OrchestrationVersion::getOrchestrationDefinitionId, orchestrationId)
                .in(OrchestrationVersion::getLifecycleState,
                        VersionLifecycleState.DRAFT, VersionLifecycleState.PUBLISHED)) > 0) {
            throw new BizException(40950, "OPENAPI_ORCHESTRATION_HAS_ACTIVE_VERSIONS");
        }
        definition.setLifecycleState(AssetLifecycleState.ARCHIVED);
        definition.setUpdatedBy(operator());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        auditService.success("ORCHESTRATION_ARCHIVED", "ORCHESTRATION", orchestrationId,
                objectMapper.createObjectNode());
    }

    public OrchestrationVersionResponse getVersion(String versionId) {
        OrchestrationVersion version = requireVersion(versionId);
        return response(requireDefinition(version.getOrchestrationDefinitionId()), version);
    }

    private void apply(OrchestrationVersion version, String schemaVersion, JsonNode content,
                       OrchestrationDefinitionValidator.ValidationResult validation) {
        version.setDefinitionSchemaVersion(schemaVersion);
        version.setDefinitionContent(content.deepCopy());
        version.setDefinitionHash(hash(content));
        version.setValidationResult(validator.validationJson(validation));
    }

    private OrchestrationDefinition requireDefinition(String id) {
        OrchestrationDefinition definition = definitionMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (definition == null || (tenantId != null && !tenantId.equals(definition.getTenantId()))) {
            throw new BizException(40450, "OPENAPI_ORCHESTRATION_NOT_FOUND");
        }
        return definition;
    }

    private OrchestrationVersion requireVersion(String id) {
        OrchestrationVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(40451, "OPENAPI_ORCHESTRATION_VERSION_NOT_FOUND");
        }
        requireDefinition(version.getOrchestrationDefinitionId());
        return version;
    }

    private void requireDraft(OrchestrationVersion version) {
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40951, "OPENAPI_PUBLISHED_ORCHESTRATION_IMMUTABLE");
        }
    }

    private void initialize(com.triobase.service.openapi.domain.model.VersionedEntity entity,
                            LocalDateTime now) {
        entity.setRowVersion(0L);
        entity.setCreatedBy(operator());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(operator());
        entity.setUpdatedAt(now);
    }

    private void initialize(com.triobase.service.openapi.domain.model.TenantEntity entity,
                            LocalDateTime now) {
        entity.setRowVersion(0L);
        entity.setCreatedBy(operator());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(operator());
        entity.setUpdatedAt(now);
    }

    private void touch(OrchestrationVersion version) {
        version.setUpdatedBy(operator());
        version.setUpdatedAt(LocalDateTime.now());
    }

    private OrchestrationVersionResponse response(
            OrchestrationDefinition definition, OrchestrationVersion version) {
        return new OrchestrationVersionResponse(
                definition.getId(), version.getId(), definition.getTenantId(),
                definition.getOrchestrationKey(), definition.getDisplayName(), definition.getOwnerId(),
                version.getVersionNumber(), version.getLifecycleState(), version.getDefinitionSchemaVersion(),
                version.getDefinitionContent(), version.getDefinitionHash(), version.getValidationResult(),
                version.getPublishedBy(), version.getPublishedAt());
    }

    private String hash(JsonNode content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash orchestration definition", exception);
        }
    }

    private String targetTenant(String requested) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            return StringUtils.hasText(requested) ? requested.trim() : null;
        }
        if (StringUtils.hasText(requested) && !current.equals(requested.trim())) {
            throw new BizException(40310, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String operator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
