package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.MappingRule;
import com.triobase.service.openapi.domain.entity.MappingSet;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.MappingDirection;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateMappingSetRequest;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.dto.MappingVersionResponse;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingSetMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingRegistryService {

    private static final int INVALID_MAPPING = 40020;
    private static final int TENANT_ACCESS_DENIED = 40310;
    private static final int MAPPING_NOT_FOUND = 40420;
    private static final int VERSION_NOT_FOUND = 40421;
    private static final int MAPPING_EXISTS = 40920;
    private static final int IMMUTABLE_VERSION = 40921;

    private final MappingSetMapper mappingSetMapper;
    private final MappingVersionMapper mappingVersionMapper;
    private final MappingRuleMapper mappingRuleMapper;
    private final StructureVersionMapper structureVersionMapper;
    private final MappingDefinitionValidator validator;
    private final IntegrationAuditService auditService;
    private final MappingPlanCompiler planCompiler;
    private final MappingContractTestService contractTestService;

    @Transactional
    public MappingVersionResponse create(CreateMappingSetRequest request) {
        validateCreateRequest(request);
        String tenantId = resolveTargetTenant(request.tenantId());
        if (mappingSetMapper.selectCount(new LambdaQueryWrapper<MappingSet>()
                .eq(MappingSet::getMappingKey, request.mappingKey().trim())
                .eq(tenantId != null, MappingSet::getTenantId, tenantId)
                .isNull(tenantId == null, MappingSet::getTenantId)) > 0) {
            throw new BizException(MAPPING_EXISTS, "OPENAPI_MAPPING_SET_ALREADY_EXISTS");
        }
        StructureVersion source = requirePublishedVersion(request.sourceStructureVersionId());
        StructureVersion target = requirePublishedVersion(request.targetStructureVersionId());
        validateDirection(request, source, target);
        JsonNode coverage = validator.validate(source.getSchemaContent(), target.getSchemaContent(), request.rules());
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();

        MappingSet set = new MappingSet();
        set.setId(UlidGenerator.nextUlid());
        set.setTenantId(tenantId);
        set.setMappingKey(request.mappingKey().trim());
        set.setDisplayName(request.displayName().trim());
        set.setDescription(request.description());
        set.setDirection(request.direction());
        set.setCanonicalStructureId(request.canonicalStructureId());
        set.setExternalStructureId(request.externalStructureId());
        set.setOwnerId(request.ownerId());
        set.setLifecycleState(AssetLifecycleState.ACTIVE);
        set.setRowVersion(0L);
        set.setCreatedBy(operator);
        set.setCreatedAt(now);
        set.setUpdatedBy(operator);
        set.setUpdatedAt(now);
        mappingSetMapper.insert(set);

        MappingVersion version = new MappingVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setMappingSetId(set.getId());
        version.setVersionNumber(1);
        version.setSourceStructureVersionId(source.getId());
        version.setTargetStructureVersionId(target.getId());
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        version.setCoverageResult(coverage);
        version.setRowVersion(0L);
        version.setCreatedBy(operator);
        version.setCreatedAt(now);
        version.setUpdatedBy(operator);
        version.setUpdatedAt(now);
        mappingVersionMapper.insert(version);
        replaceRules(version.getId(), request.rules(), now);
        auditService.success("MAPPING_SET_CREATED", "MAPPING_SET", set.getId(), coverage);
        return toResponse(set, version, loadRules(version.getId()));
    }

    @Transactional
    public MappingVersionResponse updateDraft(String mappingVersionId, List<MappingRuleRequest> rules) {
        MappingVersion version = requireVersion(mappingVersionId);
        MappingSet set = requireSet(version.getMappingSetId());
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_PUBLISHED_MAPPING_VERSION_IMMUTABLE");
        }
        StructureVersion source = requirePublishedVersion(version.getSourceStructureVersionId());
        StructureVersion target = requirePublishedVersion(version.getTargetStructureVersionId());
        JsonNode coverage = validator.validate(source.getSchemaContent(), target.getSchemaContent(), rules);
        version.setCoverageResult(coverage);
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (mappingVersionMapper.updateById(version) != 1) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_MAPPING_VERSION_CONFLICT");
        }
        mappingRuleMapper.delete(new LambdaQueryWrapper<MappingRule>()
                .eq(MappingRule::getMappingVersionId, mappingVersionId));
        replaceRules(mappingVersionId, rules, LocalDateTime.now());
        auditService.success("MAPPING_DRAFT_UPDATED", "MAPPING_VERSION", mappingVersionId, coverage);
        return toResponse(set, version, loadRules(mappingVersionId));
    }

    @Transactional
    public MappingVersionResponse createDraft(
            String mappingSetId,
            String sourceStructureVersionId,
            String targetStructureVersionId,
            List<MappingRuleRequest> rules) {
        MappingSet set = requireSet(mappingSetId);
        if (mappingVersionMapper.selectCount(new LambdaQueryWrapper<MappingVersion>()
                .eq(MappingVersion::getMappingSetId, mappingSetId)
                .eq(MappingVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_MAPPING_DRAFT_ALREADY_EXISTS");
        }
        StructureVersion source = requirePublishedVersion(sourceStructureVersionId);
        StructureVersion target = requirePublishedVersion(targetStructureVersionId);
        validateDirection(set, source, target);
        JsonNode coverage = validator.validate(source.getSchemaContent(), target.getSchemaContent(), rules);
        MappingVersion latest = mappingVersionMapper.selectOne(new LambdaQueryWrapper<MappingVersion>()
                .eq(MappingVersion::getMappingSetId, mappingSetId)
                .orderByDesc(MappingVersion::getVersionNumber)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        MappingVersion draft = new MappingVersion();
        draft.setId(UlidGenerator.nextUlid());
        draft.setMappingSetId(mappingSetId);
        draft.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        draft.setSourceStructureVersionId(sourceStructureVersionId);
        draft.setTargetStructureVersionId(targetStructureVersionId);
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        draft.setCoverageResult(coverage);
        draft.setRowVersion(0L);
        draft.setCreatedBy(currentOperator());
        draft.setCreatedAt(now);
        draft.setUpdatedBy(currentOperator());
        draft.setUpdatedAt(now);
        mappingVersionMapper.insert(draft);
        replaceRules(draft.getId(), rules, now);
        auditService.success("MAPPING_DRAFT_CREATED", "MAPPING_VERSION", draft.getId(), coverage);
        return toResponse(set, draft, loadRules(draft.getId()));
    }

    @Transactional
    public MappingVersionResponse publish(String mappingVersionId) {
        MappingVersion version = requireVersion(mappingVersionId);
        MappingSet set = requireSet(version.getMappingSetId());
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_ONLY_DRAFT_MAPPING_CAN_BE_PUBLISHED");
        }
        StructureVersion source = requirePublishedVersion(version.getSourceStructureVersionId());
        StructureVersion target = requirePublishedVersion(version.getTargetStructureVersionId());
        List<MappingRuleRequest> rules = loadRules(mappingVersionId);
        JsonNode coverage = validator.validate(source.getSchemaContent(), target.getSchemaContent(), rules);
        validator.requireCompleteCoverage(coverage);
        contractTestService.requirePassing(contractTestService.run(mappingVersionId, rules));
        MappingPlanCompiler.CompiledPlan compiledPlan = planCompiler.compile(version, rules);
        version.setCoverageResult(coverage);
        version.setCompiledPlan(compiledPlan.plan());
        version.setCompiledPlanHash(compiledPlan.hash());
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(currentOperator());
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(currentOperator());
        version.setUpdatedAt(LocalDateTime.now());
        if (mappingVersionMapper.updateById(version) != 1) {
            throw new BizException(IMMUTABLE_VERSION, "OPENAPI_MAPPING_VERSION_CONFLICT");
        }
        auditService.success("MAPPING_VERSION_PUBLISHED", "MAPPING_VERSION", mappingVersionId, coverage);
        return toResponse(set, version, rules);
    }

    public MappingVersionResponse getVersion(String mappingVersionId) {
        MappingVersion version = requireVersion(mappingVersionId);
        MappingSet set = requireSet(version.getMappingSetId());
        return toResponse(set, version, loadRules(mappingVersionId));
    }

    private void validateCreateRequest(CreateMappingSetRequest request) {
        if (request == null || !StringUtils.hasText(request.mappingKey())
                || !StringUtils.hasText(request.displayName()) || request.direction() == null
                || !StringUtils.hasText(request.canonicalStructureId())
                || !StringUtils.hasText(request.externalStructureId())
                || !StringUtils.hasText(request.sourceStructureVersionId())
                || !StringUtils.hasText(request.targetStructureVersionId())
                || !StringUtils.hasText(request.ownerId())) {
            throw new BizException(INVALID_MAPPING, "OPENAPI_MAPPING_REQUEST_INVALID");
        }
    }

    private void validateDirection(
            CreateMappingSetRequest request,
            StructureVersion source,
            StructureVersion target) {
        String expectedSource = request.direction() == MappingDirection.CANONICAL_TO_EXTERNAL
                ? request.canonicalStructureId() : request.externalStructureId();
        String expectedTarget = request.direction() == MappingDirection.CANONICAL_TO_EXTERNAL
                ? request.externalStructureId() : request.canonicalStructureId();
        if (!expectedSource.equals(source.getStructureId()) || !expectedTarget.equals(target.getStructureId())) {
            throw new BizException(INVALID_MAPPING, "OPENAPI_MAPPING_DIRECTION_STRUCTURE_MISMATCH");
        }
    }

    private void validateDirection(MappingSet set, StructureVersion source, StructureVersion target) {
        String expectedSource = set.getDirection() == MappingDirection.CANONICAL_TO_EXTERNAL
                ? set.getCanonicalStructureId() : set.getExternalStructureId();
        String expectedTarget = set.getDirection() == MappingDirection.CANONICAL_TO_EXTERNAL
                ? set.getExternalStructureId() : set.getCanonicalStructureId();
        if (!expectedSource.equals(source.getStructureId()) || !expectedTarget.equals(target.getStructureId())) {
            throw new BizException(INVALID_MAPPING, "OPENAPI_MAPPING_DIRECTION_STRUCTURE_MISMATCH");
        }
    }

    private StructureVersion requirePublishedVersion(String versionId) {
        StructureVersion version = structureVersionMapper.selectById(versionId);
        if (version == null || version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_MAPPING_REQUIRES_PUBLISHED_STRUCTURE_VERSION");
        }
        return version;
    }

    private MappingSet requireSet(String mappingSetId) {
        MappingSet set = mappingSetMapper.selectById(mappingSetId);
        if (set == null) {
            throw new BizException(MAPPING_NOT_FOUND, "OPENAPI_MAPPING_SET_NOT_FOUND");
        }
        String tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null && !tenantId.equals(set.getTenantId())) {
            throw new BizException(MAPPING_NOT_FOUND, "OPENAPI_MAPPING_SET_NOT_FOUND");
        }
        return set;
    }

    private MappingVersion requireVersion(String versionId) {
        MappingVersion version = mappingVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(VERSION_NOT_FOUND, "OPENAPI_MAPPING_VERSION_NOT_FOUND");
        }
        return version;
    }

    private void replaceRules(String versionId, List<MappingRuleRequest> rules, LocalDateTime now) {
        String operator = currentOperator();
        for (MappingRuleRequest request : rules == null ? List.<MappingRuleRequest>of() : rules) {
            MappingRule rule = new MappingRule();
            rule.setId(UlidGenerator.nextUlid());
            rule.setMappingVersionId(versionId);
            rule.setRuleOrder(request.order());
            rule.setOperationType(request.operation());
            rule.setSourcePointer(request.sourcePointer());
            rule.setTargetPointer(request.targetPointer());
            rule.setOperationConfig(request.config() == null
                    ? JsonNodeFactory.instance.objectNode() : request.config().deepCopy());
            rule.setRequiredRule(request.required());
            rule.setCreatedBy(operator);
            rule.setCreatedAt(now);
            rule.setUpdatedBy(operator);
            rule.setUpdatedAt(now);
            mappingRuleMapper.insert(rule);
        }
    }

    private List<MappingRuleRequest> loadRules(String versionId) {
        return mappingRuleMapper.selectList(new LambdaQueryWrapper<MappingRule>()
                        .eq(MappingRule::getMappingVersionId, versionId)
                        .orderByAsc(MappingRule::getRuleOrder))
                .stream()
                .sorted(Comparator.comparing(MappingRule::getRuleOrder))
                .map(rule -> new MappingRuleRequest(
                        rule.getRuleOrder(), rule.getOperationType(), rule.getSourcePointer(),
                        rule.getTargetPointer(), rule.getOperationConfig(),
                        Boolean.TRUE.equals(rule.getRequiredRule())))
                .toList();
    }

    private MappingVersionResponse toResponse(
            MappingSet set,
            MappingVersion version,
            List<MappingRuleRequest> rules) {
        return new MappingVersionResponse(
                set.getId(), version.getId(), set.getTenantId(), set.getMappingKey(), set.getDisplayName(),
                set.getDirection(), version.getVersionNumber(), version.getLifecycleState(),
                version.getSourceStructureVersionId(), version.getTargetStructureVersionId(),
                version.getCoverageResult(), rules);
    }

    private String resolveTargetTenant(String requestedTenantId) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            return StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : null;
        }
        if (StringUtils.hasText(requestedTenantId) && !current.equals(requestedTenantId.trim())) {
            throw new BizException(TENANT_ACCESS_DENIED, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
