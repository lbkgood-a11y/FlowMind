package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.dto.CreateOrchestrationRequest;
import com.triobase.service.openapi.dto.OrchestrationVersionMutationRequest;
import com.triobase.service.openapi.dto.OrchestrationVersionResponse;
import com.triobase.service.openapi.service.OrchestrationRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/management/orchestrations")
@RequiredArgsConstructor
public class OrchestrationManagementController {

    private static final String READ = "/api/v1/openapi/management/orchestrations:GET";
    private static final String WRITE = "/api/v1/openapi/management/orchestrations:POST";
    private final OrchestrationRegistryService service;

    @PostMapping
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> create(
            @Valid @RequestBody CreateOrchestrationRequest request) {
        return R.ok(service.create(request));
    }

    @GetMapping("/versions/{versionId}")
    @RequirePermission(READ)
    public R<OrchestrationVersionResponse> get(@PathVariable String versionId) {
        return R.ok(service.getVersion(versionId));
    }

    @PostMapping("/{orchestrationId}/versions")
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> createDraft(
            @PathVariable String orchestrationId,
            @Valid @RequestBody OrchestrationVersionMutationRequest request) {
        return R.ok(service.createDraft(orchestrationId, request));
    }

    @PutMapping("/versions/{versionId}")
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody OrchestrationVersionMutationRequest request) {
        return R.ok(service.updateDraft(versionId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> publish(@PathVariable String versionId) {
        return R.ok(service.publish(versionId));
    }

    @PostMapping("/versions/{versionId}/deprecate")
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> deprecate(@PathVariable String versionId) {
        return R.ok(service.deprecate(versionId));
    }

    @PostMapping("/versions/{versionId}/archive")
    @RequirePermission(WRITE)
    public R<OrchestrationVersionResponse> archiveVersion(@PathVariable String versionId) {
        return R.ok(service.archiveVersion(versionId));
    }

    @PostMapping("/{orchestrationId}/archive")
    @RequirePermission(WRITE)
    public R<Void> archive(@PathVariable String orchestrationId) {
        service.archiveDefinition(orchestrationId);
        return R.ok();
    }
}
