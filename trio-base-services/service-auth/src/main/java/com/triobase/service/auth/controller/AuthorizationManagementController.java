package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.service.auth.dto.AuthorizationAdminOptionsResponse;
import com.triobase.service.auth.dto.AuthorizationGrantResponse;
import com.triobase.service.auth.dto.AuthorizationResourceResponse;
import com.triobase.service.auth.dto.AuthorizationResourceTreeResponse;
import com.triobase.service.auth.dto.AuthorizationSyncResponse;
import com.triobase.service.auth.dto.DecisionLogResponse;
import com.triobase.service.auth.dto.FieldPolicyResponse;
import com.triobase.service.auth.dto.GuardTemplateResponse;
import com.triobase.service.auth.dto.RoleAuthorizationProfileResponse;
import com.triobase.service.auth.dto.SaveAuthorizationGrantRequest;
import com.triobase.service.auth.dto.SaveFieldPolicyRequest;
import com.triobase.service.auth.dto.SaveGuardTemplateRequest;
import com.triobase.service.auth.service.AuthorizationDecisionService;
import com.triobase.service.auth.service.AuthorizationRegistryService;
import com.triobase.service.auth.service.DataPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/authz")
@RequiredArgsConstructor
public class AuthorizationManagementController {

    private final AuthorizationRegistryService registryService;
    private final AuthorizationDecisionService decisionService;
    private final DataPolicyService dataPolicyService;

    @GetMapping("/resources")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<PageResult<AuthorizationResourceResponse>> resources(@RequestParam(required = false) String tenantId,
                                                                  @RequestParam(required = false) String ownerService,
                                                                  @RequestParam(required = false) String resourceType,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(registryService.pageResources(tenantId, ownerService, resourceType, keyword, page, size));
    }

    @GetMapping("/resources/tree")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<AuthorizationResourceTreeResponse> resourceTree(@RequestParam(required = false) String tenantId,
                                                             @RequestParam(required = false) String ownerService) {
        return R.ok(registryService.resourceTree(tenantId, ownerService));
    }

    @GetMapping("/configuration-options")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<AuthorizationAdminOptionsResponse> configurationOptions(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerService) {
        return R.ok(registryService.adminOptions(tenantId, ownerService));
    }

    @GetMapping("/roles/{roleId}/authorization-profile")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<RoleAuthorizationProfileResponse> roleAuthorizationProfile(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId) {
        RoleAuthorizationProfileResponse response = new RoleAuthorizationProfileResponse();
        response.setTenantId(registryService.effectiveTenant(tenantId));
        response.setRoleId(roleId);
        response.setFunctionGrants(registryService.listGrants(tenantId, "ROLE", roleId, null));
        response.setDataPolicies(dataPolicyService.listByRole(roleId));
        response.setFieldPolicies(registryService.listFieldPolicies(tenantId, null, "ROLE", roleId));
        return R.ok(response);
    }

    @GetMapping("/resources/stale")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<AuthorizationResourceResponse>> staleResources(@RequestParam(required = false) String tenantId,
                                                                 @RequestParam(required = false) String ownerService,
                                                                 @RequestParam(defaultValue = "1440") int staleMinutes) {
        return R.ok(registryService.staleResources(tenantId, ownerService, staleMinutes));
    }

    @PostMapping("/resources/sync")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<AuthorizationSyncResponse> syncResources(@RequestBody AuthorizationResourceSyncRequest request) {
        return R.ok(registryService.synchronize(request));
    }

    @GetMapping("/grants")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<AuthorizationGrantResponse>> grants(@RequestParam(required = false) String tenantId,
                                                      @RequestParam(required = false) String subjectType,
                                                      @RequestParam(required = false) String subjectId,
                                                      @RequestParam(required = false) String resourceCode) {
        return R.ok(registryService.listGrants(tenantId, subjectType, subjectId, resourceCode));
    }

    @PostMapping("/grants")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<AuthorizationGrantResponse> saveGrant(@RequestBody SaveAuthorizationGrantRequest request) {
        return R.ok(registryService.saveGrant(request));
    }

    @DeleteMapping("/grants/{id}")
    @RequirePermission("/api/v1/authz/**:DELETE")
    public R<Void> deleteGrant(@PathVariable String id) {
        registryService.deleteGrant(id);
        return R.ok();
    }

    @PostMapping("/decisions/preview")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<AuthorizationDecisionResponse> preview(@RequestBody AuthorizationDecisionRequest request) {
        request.setPreviewMode(true);
        request.setEnforcementMode(false);
        return R.ok(decisionService.decide(request));
    }

    @PostMapping("/decisions/batch-preview")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<AuthorizationBatchDecisionResponse> batchPreview(@RequestBody AuthorizationBatchDecisionRequest request) {
        if (request != null && request.getDecisions() != null) {
            request.getDecisions().forEach(decision -> {
                decision.setPreviewMode(true);
                decision.setEnforcementMode(false);
            });
        }
        return R.ok(decisionService.batchDecide(request));
    }

    @GetMapping("/field-policies")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<FieldPolicyResponse>> fieldPolicies(@RequestParam(required = false) String tenantId,
                                                      @RequestParam(required = false) String resourceCode,
                                                      @RequestParam(required = false) String subjectType,
                                                      @RequestParam(required = false) String subjectId) {
        return R.ok(registryService.listFieldPolicies(tenantId, resourceCode, subjectType, subjectId));
    }

    @PostMapping("/field-policies")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<FieldPolicyResponse> saveFieldPolicy(@RequestBody SaveFieldPolicyRequest request) {
        return R.ok(registryService.saveFieldPolicy(request));
    }

    @DeleteMapping("/field-policies/{id}")
    @RequirePermission("/api/v1/authz/**:DELETE")
    public R<Void> deleteFieldPolicy(@PathVariable String id) {
        registryService.deleteFieldPolicy(id);
        return R.ok();
    }

    @GetMapping("/guard-templates")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<GuardTemplateResponse>> guardTemplates(@RequestParam(required = false) String tenantId,
                                                         @RequestParam(required = false) String ownerService) {
        return R.ok(registryService.listGuardTemplates(tenantId, ownerService));
    }

    @PostMapping("/guard-templates")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<GuardTemplateResponse> saveGuardTemplate(@RequestBody SaveGuardTemplateRequest request) {
        return R.ok(registryService.saveGuardTemplate(request));
    }

    @PutMapping("/guard-templates/{id}/status")
    @RequirePermission("/api/v1/authz/**:PUT")
    public R<Void> updateGuardTemplateStatus(@PathVariable String id, @RequestParam Integer status) {
        registryService.updateGuardTemplateStatus(id, status);
        return R.ok();
    }

    @GetMapping("/decision-logs")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<PageResult<DecisionLogResponse>> decisionLogs(@RequestParam(required = false) String tenantId,
                                                           @RequestParam(required = false) String userId,
                                                           @RequestParam(required = false) String resourceCode,
                                                           @RequestParam(required = false) String actionCode,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return R.ok(registryService.pageDecisionLogs(tenantId, userId, resourceCode, actionCode, startTime, endTime, page, size));
    }

    @GetMapping("/decision-logs/{id}")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<DecisionLogResponse> decisionLogDetail(@PathVariable String id) {
        return R.ok(registryService.getDecisionLog(id));
    }
}
