package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.dto.CreateRouteRequest;
import com.triobase.service.openapi.dto.PublishReleaseRequest;
import com.triobase.service.openapi.dto.ReleaseSnapshotResponse;
import com.triobase.service.openapi.dto.RollbackReleaseRequest;
import com.triobase.service.openapi.dto.RouteResolutionContext;
import com.triobase.service.openapi.dto.RouteVersionMutationRequest;
import com.triobase.service.openapi.dto.RouteVersionResponse;
import com.triobase.service.openapi.service.ReleaseManagementService;
import com.triobase.service.openapi.service.RouteRegistryService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/openapi/management")
@RequiredArgsConstructor
public class RouteReleaseManagementController {

    private static final String READ = "/api/v1/openapi/management/routes:GET";
    private static final String WRITE = "/api/v1/openapi/management/routes:POST";
    private final RouteRegistryService routeRegistryService;
    private final ReleaseManagementService releaseManagementService;

    @PostMapping("/routes")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> create(@Valid @RequestBody CreateRouteRequest request) {
        return R.ok(routeRegistryService.create(request));
    }

    @GetMapping("/routes/versions/{versionId}")
    @RequirePermission(READ)
    public R<RouteVersionResponse> getVersion(@PathVariable String versionId) {
        return R.ok(routeRegistryService.getVersion(versionId));
    }

    @PostMapping("/routes/{routeId}/versions")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> createDraft(
            @PathVariable String routeId,
            @RequestParam Environment environment,
            @Valid @RequestBody RouteVersionMutationRequest request) {
        return R.ok(routeRegistryService.createDraft(routeId, environment, request));
    }

    @PutMapping("/routes/versions/{versionId}")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody RouteVersionMutationRequest request) {
        return R.ok(routeRegistryService.updateDraft(versionId, request));
    }

    @PostMapping("/routes/versions/{versionId}/publish")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> publishRoute(@PathVariable String versionId) {
        return R.ok(routeRegistryService.publish(versionId));
    }

    @PostMapping("/routes/versions/{versionId}/deprecate")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> deprecateRoute(@PathVariable String versionId) {
        return R.ok(routeRegistryService.deprecate(versionId));
    }

    @PostMapping("/routes/versions/{versionId}/archive")
    @RequirePermission(WRITE)
    public R<RouteVersionResponse> archiveRouteVersion(@PathVariable String versionId) {
        return R.ok(routeRegistryService.archiveVersion(versionId));
    }

    @PostMapping("/routes/{routeId}/archive")
    @RequirePermission(WRITE)
    public R<Void> archiveRoute(@PathVariable String routeId) {
        routeRegistryService.archiveRoute(routeId);
        return R.ok();
    }

    @PostMapping("/routes/{routeKey}/resolve-preview")
    @RequirePermission(READ)
    public R<RouteVersionResponse> resolvePreview(
            @PathVariable String routeKey,
            @RequestParam Environment environment,
            @RequestBody RouteResolutionContext context) {
        return R.ok(routeRegistryService.resolve(routeKey, environment, context));
    }

    @PostMapping("/routes/versions/{versionId}/releases")
    @RequirePermission(WRITE)
    public R<ReleaseSnapshotResponse> publishRelease(
            @PathVariable String versionId,
            @RequestBody(required = false) PublishReleaseRequest request) {
        return R.ok(releaseManagementService.publish(
                versionId, request == null ? null : request.releaseNotes()));
    }

    @PostMapping("/releases/{releaseId}/activate")
    @RequirePermission(WRITE)
    public R<CompiledRouteRelease> activate(@PathVariable String releaseId) {
        return R.ok(releaseManagementService.activate(releaseId));
    }

    @PostMapping("/routes/{routeId}/rollback")
    @RequirePermission(WRITE)
    public R<CompiledRouteRelease> rollback(
            @PathVariable String routeId,
            @Valid @RequestBody RollbackReleaseRequest request) {
        return R.ok(releaseManagementService.rollback(
                routeId, request.environment(), request.targetReleaseId()));
    }

    @PostMapping("/releases/{releaseId}/deprecate")
    @RequirePermission(WRITE)
    public R<ReleaseSnapshotResponse> deprecate(@PathVariable String releaseId) {
        return R.ok(releaseManagementService.deprecate(releaseId));
    }

    @PostMapping("/releases/{releaseId}/archive")
    @RequirePermission(WRITE)
    public R<ReleaseSnapshotResponse> archiveRelease(@PathVariable String releaseId) {
        return R.ok(releaseManagementService.archive(releaseId));
    }

    @GetMapping("/routes/{routeId}/releases")
    @RequirePermission(READ)
    public R<List<ReleaseSnapshotResponse>> releaseHistory(
            @PathVariable String routeId,
            @RequestParam Environment environment) {
        return R.ok(releaseManagementService.history(routeId, environment));
    }

    @GetMapping("/routes/{routeKey}/active-release")
    @RequirePermission(READ)
    public R<CompiledRouteRelease> activeRelease(
            @PathVariable String routeKey,
            @RequestParam Environment environment) {
        return R.ok(releaseManagementService.resolveActive(routeKey, environment));
    }
}
