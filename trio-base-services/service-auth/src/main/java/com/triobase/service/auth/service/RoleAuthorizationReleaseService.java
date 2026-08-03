package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.PublishRoleAuthorizationRequest;
import com.triobase.service.auth.dto.RoleAuthorizationCompilationPlan;
import com.triobase.service.auth.dto.RoleAuthorizationReleaseResponse;
import com.triobase.service.auth.dto.RoleAuthorizationValidationResponse;
import com.triobase.service.auth.entity.RoleAuthorizationDraftStatus;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import com.triobase.service.auth.entity.SysRoleAuthRelease;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.DataPolicyDimensionMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import com.triobase.service.auth.mapper.RoleAuthReleaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationReleaseService {

    private static final int VALIDATION_MINUTES = 15;
    private static final String RELEASE_MANAGED_DESCRIPTION_PREFIX = "页面功能发布版本 ";

    private final RoleAuthorizationCompiler compiler;
    private final RoleAuthDraftMapper draftMapper;
    private final RoleAuthIntentMapper intentMapper;
    private final RoleAuthReleaseMapper releaseMapper;
    private final RoleAuthActiveReleaseMapper activeReleaseMapper;
    private final RoleAuthCompiledEvidenceMapper evidenceMapper;
    private final AuthGrantMapper grantMapper;
    private final DataPolicyMapper dataPolicyMapper;
    private final DataPolicyDimensionMapper dimensionMapper;
    private final AuthFieldPolicyMapper fieldPolicyMapper;
    private final AuthorizationRegistryService authorizationRegistryService;
    private final AuthorizationVersionService versionService;
    private final RoleAuthorizationAuditService auditService;
    private final AuthorizationManagementModeService managementModeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RoleAuthorizationValidationResponse validate(String requestedTenantId, String draftId,
                                                        long expectedVersion) {
        return validate(requestedTenantId, draftId, expectedVersion, false);
    }

    @Transactional
    public RoleAuthorizationValidationResponse validate(String requestedTenantId, String draftId,
                                                        long expectedVersion,
                                                        boolean acknowledgePermissionExpansion) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthDraft draft = requireDraft(tenantId, draftId);
        if (draft.getIntentVersion() == null || draft.getIntentVersion() != expectedVersion) {
            throw new BizException(40993, "ROLE_AUTH_DRAFT_VERSION_CONFLICT");
        }
        if (draft.getMigrationExpansionDetected() != null
                && draft.getMigrationExpansionDetected() == 1
                && !acknowledgePermissionExpansion) {
            throw new BizException(40997,
                    "迁移草稿可能扩大现有权限，请逐项复核并明确确认后再校验");
        }
        RoleAuthorizationCompilationPlan plan = compiler.compile(tenantId, draftId);
        String planJson = writeJson(plan);
        String planHash = sha256(planJson);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(VALIDATION_MINUTES);
        String actor = currentActor();
        long authorityVersion = authorizationRegistryService.currentGrantVersion();
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString((tenantId + "\n"
                + draft.getRoleId() + "\n" + draftId + "\n" + expectedVersion + "\n"
                + plan.getCatalogVersion() + "\n" + actor + "\n" + authorityVersion + "\n"
                + planHash + "\n" + expiresAt + "\n"
                + Base64.getEncoder().encodeToString(nonce)).getBytes(StandardCharsets.UTF_8));

        draft.setDraftStatus(RoleAuthorizationDraftStatus.VALIDATED.name());
        draft.setValidationTokenHash(sha256(token));
        draft.setValidationPlanHash(planHash);
        draft.setValidatedBy(actor);
        draft.setValidationAuthorityVersion(authorityVersion);
        draft.setValidatedAt(LocalDateTime.now());
        draft.setValidationExpiresAt(expiresAt);
        draft.setValidationSummary(plan.getBusinessSummary());
        draft.setMigrationReviewRequired((short) 0);
        if (draft.getMigrationExpansionDetected() != null
                && draft.getMigrationExpansionDetected() == 1
                && acknowledgePermissionExpansion) {
            draft.setMigrationExpansionAcknowledged((short) 1);
        }
        draft.setUpdatedBy(actor);
        draftMapper.updateById(draft);
        auditService.record(tenantId, draft.getRoleId(), draftId, null,
                "VALIDATED", plan.getBusinessSummary(), plan);
        return RoleAuthorizationValidationResponse.builder()
                .validationToken(token)
                .expiresAt(expiresAt)
                .businessSummary(plan.getBusinessSummary())
                .blockingErrors(List.of())
                .warnings(List.of())
                .compilation(plan)
                .affectedUserCount(auditService.affectedUsers(draft.getRoleId()))
                .build();
    }

    @Transactional
    public RoleAuthorizationReleaseResponse publish(String draftId, PublishRoleAuthorizationRequest request) {
        if (request == null || request.getExpectedVersion() == null
                || !StringUtils.hasText(request.getValidationToken())) {
            throw new BizException(40091, "ROLE_AUTH_VALIDATION_REQUIRED");
        }
        String tenantId = authorizationRegistryService.effectiveTenant(request.getTenantId());
        managementModeService.requirePagePublicationAllowed(tenantId);
        SysRoleAuthDraft draft = requireDraft(tenantId, draftId);
        verifyValidation(draft, request);
        RoleAuthorizationCompilationPlan plan = compiler.compile(tenantId, draftId);
        String planJson = writeJson(plan);
        if (!sha256(planJson).equals(draft.getValidationPlanHash())) {
            throw new BizException(40993, "ROLE_AUTH_VALIDATION_STALE");
        }

        SysRoleAuthActiveRelease previousActive = activeRelease(tenantId, draft.getRoleId());
        long releaseNumber = nextReleaseNumber(tenantId, draft.getRoleId());
        SysRoleAuthRelease release = new SysRoleAuthRelease();
        release.setTenantId(tenantId);
        release.setRoleId(draft.getRoleId());
        release.setCatalogId(draft.getCatalogId());
        release.setDraftId(draft.getId());
        release.setPreviousReleaseId(previousActive != null ? previousActive.getReleaseId() : null);
        release.setReleaseNumber(releaseNumber);
        release.setIntentVersion(draft.getIntentVersion());
        release.setCatalogVersion(plan.getCatalogVersion());
        release.setValidationHash(draft.getValidationPlanHash());
        release.setIntentSnapshot(writeJson(intentMapper.selectList(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, tenantId)
                .eq(SysRoleAuthIntent::getDraftId, draftId))));
        release.setCompiledSnapshot(planJson);
        release.setBusinessSummary(plan.getBusinessSummary());
        release.setPublishedBy(currentActor());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);

        applyPlan(release, plan, true);
        activateRelease(tenantId, draft.getRoleId(), release.getId(), "PUBLISH");
        draft.setDraftStatus(RoleAuthorizationDraftStatus.PUBLISHED.name());
        draft.setUpdatedBy(currentActor());
        draftMapper.updateById(draft);
        bumpRuntimeVersions();
        auditService.record(tenantId, draft.getRoleId(), draftId, release.getId(),
                "PUBLISHED", plan.getBusinessSummary(), plan);
        return RoleAuthorizationReleaseResponse.from(release);
    }

    @Transactional
    public RoleAuthorizationReleaseResponse rollback(String requestedTenantId, String roleId, String releaseId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthRelease release = releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                .eq(SysRoleAuthRelease::getTenantId, tenantId)
                .eq(SysRoleAuthRelease::getRoleId, roleId)
                .eq(SysRoleAuthRelease::getId, releaseId));
        if (release == null) {
            throw new BizException(40496, "ROLE_AUTH_RELEASE_NOT_FOUND");
        }
        RoleAuthorizationCompilationPlan plan;
        try {
            plan = objectMapper.readValue(release.getCompiledSnapshot(), RoleAuthorizationCompilationPlan.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(40996, "ROLE_AUTH_RELEASE_SNAPSHOT_INVALID");
        }
        applyPlan(release, plan, false);
        activateRelease(tenantId, roleId, releaseId, "ROLLBACK");
        bumpRuntimeVersions();
        auditService.record(tenantId, roleId, release.getDraftId(), releaseId,
                "ROLLED_BACK", "已恢复第 " + release.getReleaseNumber() + " 版权限："
                        + release.getBusinessSummary(), plan);
        return RoleAuthorizationReleaseResponse.from(release);
    }

    public List<RoleAuthorizationReleaseResponse> releases(String requestedTenantId, String roleId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        return releaseMapper.selectList(new LambdaQueryWrapper<SysRoleAuthRelease>()
                        .eq(SysRoleAuthRelease::getTenantId, tenantId)
                        .eq(SysRoleAuthRelease::getRoleId, roleId)
                        .orderByDesc(SysRoleAuthRelease::getReleaseNumber))
                .stream().map(RoleAuthorizationReleaseResponse::from).toList();
    }

    public void recordValidationFailure(String requestedTenantId, String draftId, RuntimeException failure) {
        recordFailure(requestedTenantId, draftId, "VALIDATION_FAILED", "权限校验失败", failure);
    }

    public void recordPublishFailure(String requestedTenantId, String draftId, RuntimeException failure) {
        recordFailure(requestedTenantId, draftId, "PUBLISH_FAILED", "权限发布失败，原线上版本保持不变", failure);
    }

    private void recordFailure(String requestedTenantId, String draftId, String eventType,
                               String summary, RuntimeException failure) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthDraft draft = requireDraft(tenantId, draftId);
        auditService.record(tenantId, draft.getRoleId(), draftId, null, eventType, summary,
                Map.of("failureType", failure.getClass().getSimpleName(),
                        "failureMessage", failure.getMessage() == null ? "unknown" : failure.getMessage()));
    }

    private void applyPlan(SysRoleAuthRelease release, RoleAuthorizationCompilationPlan plan,
                           boolean recordEvidence) {
        String tenantId = release.getTenantId();
        String roleId = release.getRoleId();
        grantMapper.delete(new LambdaQueryWrapper<SysAuthGrant>()
                .eq(SysAuthGrant::getTenantId, tenantId)
                .eq(SysAuthGrant::getSubjectType, "ROLE")
                .eq(SysAuthGrant::getSubjectId, roleId)
                .likeRight(SysAuthGrant::getDescription, RELEASE_MANAGED_DESCRIPTION_PREFIX));
        for (RoleAuthorizationCompilationPlan.GrantProjection projection : plan.getGrants()) {
            SysAuthGrant grant = new SysAuthGrant();
            grant.setTenantId(tenantId);
            grant.setSubjectType("ROLE");
            grant.setSubjectId(roleId);
            grant.setResourceCode(projection.getResourceCode());
            grant.setActionCode(projection.getActionCode());
            grant.setEffect(projection.getEffect());
            grant.setStatus((short) 1);
            grant.setDescription(RELEASE_MANAGED_DESCRIPTION_PREFIX + release.getReleaseNumber());
            grant.setCreatedBy(currentActor());
            grant.setUpdatedBy(currentActor());
            grantMapper.insert(grant);
            if (recordEvidence) {
                evidence(release, projection.getCapabilityCode(), "GRANT",
                        projection.getResourceCode() + ":" + projection.getActionCode(),
                        projection.getResourceCode(), projection.getActionCode(), projection.getEffect(), projection);
            }
        }

        List<SysDataPolicy> oldPolicies = dataPolicyMapper.selectList(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, tenantId)
                .eq(SysDataPolicy::getSubjectType, "ROLE")
                .eq(SysDataPolicy::getSubjectId, roleId)
                .likeRight(SysDataPolicy::getDescription, RELEASE_MANAGED_DESCRIPTION_PREFIX));
        if (!oldPolicies.isEmpty()) {
            dimensionMapper.delete(new LambdaQueryWrapper<SysDataPolicyDimension>()
                    .in(SysDataPolicyDimension::getPolicyId,
                            oldPolicies.stream().map(SysDataPolicy::getId).toList()));
        }
        dataPolicyMapper.delete(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, tenantId)
                .eq(SysDataPolicy::getSubjectType, "ROLE")
                .eq(SysDataPolicy::getSubjectId, roleId)
                .likeRight(SysDataPolicy::getDescription, RELEASE_MANAGED_DESCRIPTION_PREFIX));
        for (RoleAuthorizationCompilationPlan.DataProjection projection : plan.getDataPolicies()) {
            SysDataPolicy policy = new SysDataPolicy();
            policy.setTenantId(tenantId);
            policy.setSubjectType("ROLE");
            policy.setSubjectId(roleId);
            policy.setResourceCode(projection.getResourceCode());
            policy.setActionCode(projection.getActionCode());
            policy.setEffect("ALLOW");
            policy.setCombineMode("ANY");
            policy.setStatus((short) 1);
            policy.setDescription(RELEASE_MANAGED_DESCRIPTION_PREFIX + release.getReleaseNumber());
            policy.setCreatedBy(currentActor());
            policy.setUpdatedBy(currentActor());
            dataPolicyMapper.insert(policy);
            SysDataPolicyDimension dimension = new SysDataPolicyDimension();
            dimension.setPolicyId(policy.getId());
            dimension.setDimensionCode("ORG");
            dimension.setScopeType(projection.getScopeType());
            dimension.setOrgUnitIds(writeJson(projection.getOrganizationIds()));
            dimension.setSortOrder(10);
            dimension.setCreatedBy(currentActor());
            dimension.setUpdatedBy(currentActor());
            dimensionMapper.insert(dimension);
            if (recordEvidence) {
                evidence(release, projection.getCapabilityCode(), "DATA_POLICY",
                        projection.getResourceCode() + ":" + projection.getActionCode(),
                        projection.getResourceCode(), projection.getActionCode(), "ALLOW", projection);
            }
        }

        fieldPolicyMapper.delete(new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getTenantId, tenantId)
                .eq(SysAuthFieldPolicy::getSubjectType, "ROLE")
                .eq(SysAuthFieldPolicy::getSubjectId, roleId)
                .likeRight(SysAuthFieldPolicy::getDescription, RELEASE_MANAGED_DESCRIPTION_PREFIX));
        for (RoleAuthorizationCompilationPlan.FieldProjection projection : plan.getFieldPolicies()) {
            SysAuthFieldPolicy policy = new SysAuthFieldPolicy();
            policy.setTenantId(tenantId);
            policy.setSubjectType("ROLE");
            policy.setSubjectId(roleId);
            policy.setResourceCode(projection.getResourceCode());
            policy.setFieldKey(projection.getFieldKey());
            policy.setReadMode(projection.getReadMode());
            policy.setWriteMode(projection.getWriteMode());
            policy.setMaskStrategy(projection.getMaskStrategy());
            policy.setEffect("ALLOW");
            policy.setStatus((short) 1);
            policy.setDescription(RELEASE_MANAGED_DESCRIPTION_PREFIX + release.getReleaseNumber());
            policy.setCreatedBy(currentActor());
            policy.setUpdatedBy(currentActor());
            fieldPolicyMapper.insert(policy);
            if (recordEvidence) {
                evidence(release, projection.getCapabilityCode(), "FIELD_POLICY",
                        projection.getResourceCode() + ":" + projection.getFieldKey(),
                        projection.getResourceCode(), null, "ALLOW", projection);
            }
        }
        for (RoleAuthorizationCompilationPlan.GuardProjection projection : plan.getGuards()) {
            if (recordEvidence) {
                evidence(release, projection.getCapabilityCode(), "GUARD",
                        projection.getResourceCode() + ":" + projection.getActionCode() + ":" + projection.getGuardCode(),
                        projection.getResourceCode(), projection.getActionCode(), null, projection);
            }
        }
    }

    private void evidence(SysRoleAuthRelease release, String capabilityCode, String type,
                          String key, String resourceCode, String actionCode, String effect, Object snapshot) {
        SysRoleAuthCompiledEvidence evidence = new SysRoleAuthCompiledEvidence();
        evidence.setTenantId(release.getTenantId());
        evidence.setReleaseId(release.getId());
        evidence.setCapabilityCode(capabilityCode);
        evidence.setProjectionType(type);
        evidence.setProjectionKey(key);
        evidence.setResourceCode(resourceCode);
        evidence.setActionCode(actionCode);
        evidence.setEffect(effect);
        evidence.setProjectionSnapshot(writeJson(snapshot));
        evidence.setCreatedAt(LocalDateTime.now());
        evidenceMapper.insert(evidence);
    }

    private void activateRelease(String tenantId, String roleId, String releaseId, String type) {
        activeReleaseMapper.delete(new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                .eq(SysRoleAuthActiveRelease::getTenantId, tenantId)
                .eq(SysRoleAuthActiveRelease::getRoleId, roleId));
        SysRoleAuthActiveRelease active = new SysRoleAuthActiveRelease();
        active.setTenantId(tenantId);
        active.setRoleId(roleId);
        active.setReleaseId(releaseId);
        active.setActivatedBy(currentActor());
        active.setActivatedAt(LocalDateTime.now());
        active.setActivationType(type);
        activeReleaseMapper.insert(active);
    }

    private void verifyValidation(SysRoleAuthDraft draft, PublishRoleAuthorizationRequest request) {
        if (!RoleAuthorizationDraftStatus.VALIDATED.name().equals(draft.getDraftStatus())
                || !request.getExpectedVersion().equals(draft.getIntentVersion())
                || draft.getValidationExpiresAt() == null
                || draft.getValidationExpiresAt().isBefore(LocalDateTime.now())
                || !currentActor().equals(draft.getValidatedBy())
                || draft.getValidationAuthorityVersion() == null
                || draft.getValidationAuthorityVersion() != authorizationRegistryService.currentGrantVersion()
                || !sha256(request.getValidationToken()).equals(draft.getValidationTokenHash())) {
            throw new BizException(40993, "ROLE_AUTH_VALIDATION_STALE");
        }
    }

    private SysRoleAuthDraft requireDraft(String tenantId, String draftId) {
        SysRoleAuthDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getId, draftId));
        if (draft == null) {
            throw new BizException(40491, "ROLE_AUTH_DRAFT_NOT_FOUND");
        }
        return draft;
    }

    private SysRoleAuthActiveRelease activeRelease(String tenantId, String roleId) {
        return activeReleaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthActiveRelease>()
                .eq(SysRoleAuthActiveRelease::getTenantId, tenantId)
                .eq(SysRoleAuthActiveRelease::getRoleId, roleId));
    }

    private long nextReleaseNumber(String tenantId, String roleId) {
        SysRoleAuthRelease latest = releaseMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthRelease>()
                .eq(SysRoleAuthRelease::getTenantId, tenantId)
                .eq(SysRoleAuthRelease::getRoleId, roleId)
                .orderByDesc(SysRoleAuthRelease::getReleaseNumber)
                .last("LIMIT 1"));
        return latest == null ? 1L : latest.getReleaseNumber() + 1L;
    }

    private void bumpRuntimeVersions() {
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        versionService.bump(AuthorizationVersionService.GRANT);
        versionService.bump(AuthorizationVersionService.DATA_POLICY);
        versionService.bump(AuthorizationVersionService.FIELD_POLICY);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(40996, "ROLE_AUTH_SNAPSHOT_INVALID");
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String currentActor() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
