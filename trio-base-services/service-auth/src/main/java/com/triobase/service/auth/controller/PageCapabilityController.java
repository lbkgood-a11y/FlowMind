package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.dto.PageCapabilityDiagnosticResponse;
import com.triobase.service.auth.dto.PageCapabilityResponse;
import com.triobase.service.auth.dto.PageCapabilitySimulationRequest;
import com.triobase.service.auth.dto.PageCapabilitySimulationResponse;
import com.triobase.service.auth.dto.RoleAuthorizationDriftResponse;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.service.PageCapabilityCatalogService;
import com.triobase.service.auth.service.PageCapabilitySimulationService;
import com.triobase.service.auth.service.RoleAuthorizationDriftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/authz/page-capabilities")
@RequiredArgsConstructor
public class PageCapabilityController {

    private final PageCapabilityCatalogService catalogService;
    private final PageCapabilitySimulationService simulationService;
    private final RoleAuthorizationDriftService driftService;

    @GetMapping
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<PageCapabilityResponse>> implementationCatalog(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String catalogId) {
        return R.ok(catalogService.implementationCatalog(tenantId, catalogId));
    }

    @GetMapping("/diagnostics")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<PageCapabilityDiagnosticResponse>> diagnostics(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String catalogId) {
        return R.ok(catalogService.diagnostics(tenantId, catalogId));
    }

    @PostMapping("/manifests/sync")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<SysAuthPageCatalog> synchronize(@RequestBody PageCapabilityManifestSyncRequest request) {
        return R.ok(catalogService.synchronize(request));
    }

    @PostMapping("/catalogs/{catalogId}/activate")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<SysAuthPageCatalog> activate(@PathVariable String catalogId,
                                          @RequestParam(required = false) String tenantId) {
        return R.ok(catalogService.activate(tenantId, catalogId));
    }

    @PostMapping("/{capabilityId}/simulate")
    @RequirePermission("/api/v1/authz/**:POST")
    public R<PageCapabilitySimulationResponse> simulate(
            @PathVariable String capabilityId,
            @RequestBody PageCapabilitySimulationRequest request) {
        return R.ok(simulationService.simulate(capabilityId, request));
    }

    @GetMapping("/drifts")
    @RequirePermission("/api/v1/authz/**:GET")
    public R<List<RoleAuthorizationDriftResponse>> drifts(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String roleId) {
        return R.ok(driftService.openDrifts(tenantId, roleId));
    }
}
