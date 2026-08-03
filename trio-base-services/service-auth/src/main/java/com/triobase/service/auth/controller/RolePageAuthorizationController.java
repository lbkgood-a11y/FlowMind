package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.PublishRoleAuthorizationRequest;
import com.triobase.service.auth.dto.LegacyRoleAuthorizationAnalysisResponse;
import com.triobase.service.auth.dto.ReplaceRoleCapabilityIntentRequest;
import com.triobase.service.auth.dto.RoleAuthorizationDraftResponse;
import com.triobase.service.auth.dto.RoleAuthorizationReleaseResponse;
import com.triobase.service.auth.dto.RoleAuthorizationValidationResponse;
import com.triobase.service.auth.dto.ValidateRoleAuthorizationRequest;
import com.triobase.service.auth.dto.AuthorizationCompatibilityDashboardResponse;
import com.triobase.service.auth.service.RoleAuthorizationReleaseService;
import com.triobase.service.auth.service.LegacyRoleAuthorizationMigrationService;
import com.triobase.service.auth.service.AuthorizationManagementModeService;
import com.triobase.service.auth.entity.SysAuthTenantManagementMode;
import com.triobase.service.auth.service.RolePageCapabilityStore;
import com.triobase.service.auth.service.AuthorizationCompatibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/authz")
@RequiredArgsConstructor
public class RolePageAuthorizationController {

    private final RolePageCapabilityStore capabilityStore;
    private final RoleAuthorizationReleaseService releaseService;
    private final LegacyRoleAuthorizationMigrationService migrationService;
    private final AuthorizationManagementModeService managementModeService;
    private final AuthorizationCompatibilityService compatibilityService;

    @PostMapping("/roles/{roleId}/authorization-drafts")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<RoleAuthorizationDraftResponse> getOrCreateDraft(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId) {
        return R.ok(capabilityStore.getOrCreateDraftView(tenantId, roleId));
    }

    @GetMapping("/role-authorization-drafts/{draftId}")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<RoleAuthorizationDraftResponse> draft(
            @PathVariable String draftId,
            @RequestParam(required = false) String tenantId) {
        return R.ok(capabilityStore.draftView(tenantId, draftId));
    }

    @PutMapping("/role-authorization-drafts/{draftId}/intent")
    @RequirePermission("/api/v1/authz/**:PUT")
    public R<RoleAuthorizationDraftResponse> replaceIntent(
            @PathVariable String draftId,
            @RequestBody ReplaceRoleCapabilityIntentRequest request) {
        return R.ok(capabilityStore.replaceIntentView(draftId, request));
    }

    @PostMapping("/role-authorization-drafts/{draftId}/validate")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<RoleAuthorizationValidationResponse> validate(
            @PathVariable String draftId,
            @RequestBody ValidateRoleAuthorizationRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new BizException(40091, "ROLE_AUTH_DRAFT_VERSION_REQUIRED");
        }
        try {
            return R.ok(releaseService.validate(request.getTenantId(), draftId, request.getExpectedVersion(),
                    Boolean.TRUE.equals(request.getAcknowledgePermissionExpansion())));
        } catch (RuntimeException failure) {
            try {
                releaseService.recordValidationFailure(request.getTenantId(), draftId, failure);
            } catch (RuntimeException ignored) {
                // Preserve the original validation error when audit persistence is unavailable.
            }
            throw failure;
        }
    }

    @PostMapping("/role-authorization-drafts/{draftId}/publish")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<RoleAuthorizationReleaseResponse> publish(
            @PathVariable String draftId,
            @RequestBody PublishRoleAuthorizationRequest request) {
        try {
            return R.ok(releaseService.publish(draftId, request));
        } catch (RuntimeException failure) {
            try {
                releaseService.recordPublishFailure(request != null ? request.getTenantId() : null,
                        draftId, failure);
            } catch (RuntimeException ignored) {
                // Preserve the original publication error when audit persistence is unavailable.
            }
            throw failure;
        }
    }

    @GetMapping("/roles/{roleId}/authorization-releases")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<RoleAuthorizationReleaseResponse>> releases(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId) {
        return R.ok(releaseService.releases(tenantId, roleId));
    }

    @PostMapping("/roles/{roleId}/authorization-releases/{releaseId}/rollback")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<RoleAuthorizationReleaseResponse> rollback(
            @PathVariable String roleId,
            @PathVariable String releaseId,
            @RequestParam(required = false) String tenantId) {
        RoleAuthorizationReleaseResponse restored = releaseService.rollback(tenantId, roleId, releaseId);
        capabilityStore.rebaseEditableDraft(tenantId, roleId, releaseId);
        return R.ok(restored);
    }

    @GetMapping("/roles/{roleId}/authorization-migration-analysis")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<LegacyRoleAuthorizationAnalysisResponse> migrationAnalysis(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId) {
        return R.ok(migrationService.analyze(tenantId, roleId));
    }

    @PostMapping("/roles/{roleId}/authorization-migration-drafts")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<LegacyRoleAuthorizationAnalysisResponse> createMigrationDraft(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId) {
        return R.ok(migrationService.createReviewDraft(tenantId, roleId));
    }

    @GetMapping("/management-mode")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<SysAuthTenantManagementMode> managementMode(
            @RequestParam(required = false) String tenantId) {
        return R.ok(managementModeService.current(tenantId));
    }

    @PutMapping("/management-mode")
    @RequirePermission("/api/v1/authz/**:PUT")
    public R<SysAuthTenantManagementMode> updateManagementMode(
            @RequestParam(required = false) String tenantId,
            @RequestParam String mode) {
        return R.ok(managementModeService.update(tenantId, mode));
    }

    @GetMapping("/compatibility-dashboard")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<AuthorizationCompatibilityDashboardResponse> compatibilityDashboard(
            @RequestParam(required = false) String tenantId) {
        return R.ok(compatibilityService.assess(tenantId));
    }
}
