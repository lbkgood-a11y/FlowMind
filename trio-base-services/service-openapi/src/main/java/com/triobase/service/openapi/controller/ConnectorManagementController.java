package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.dto.ConnectorVersionMutationRequest;
import com.triobase.service.openapi.dto.ConnectorVersionResponse;
import com.triobase.service.openapi.dto.CreateConnectorRequest;
import com.triobase.service.openapi.service.ConnectorRegistryService;
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
@RequestMapping("/api/v1/openapi/management/connectors")
@RequiredArgsConstructor
public class ConnectorManagementController {

    private static final String READ = "/api/v1/openapi/management/connectors:GET";
    private static final String WRITE = "/api/v1/openapi/management/connectors:POST";
    private final ConnectorRegistryService connectorRegistryService;

    @PostMapping
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> create(@Valid @RequestBody CreateConnectorRequest request) {
        return R.ok(connectorRegistryService.create(request));
    }

    @GetMapping("/versions/{versionId}")
    @RequirePermission(READ)
    public R<ConnectorVersionResponse> getVersion(@PathVariable String versionId) {
        return R.ok(connectorRegistryService.getVersion(versionId));
    }

    @PostMapping("/{connectorId}/versions")
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> createDraft(
            @PathVariable String connectorId,
            @Valid @RequestBody ConnectorVersionMutationRequest request) {
        return R.ok(connectorRegistryService.createDraft(connectorId, request));
    }

    @PutMapping("/versions/{versionId}")
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody ConnectorVersionMutationRequest request) {
        return R.ok(connectorRegistryService.updateDraft(versionId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> publish(@PathVariable String versionId) {
        return R.ok(connectorRegistryService.publish(versionId));
    }

    @PostMapping("/versions/{versionId}/deprecate")
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> deprecate(@PathVariable String versionId) {
        return R.ok(connectorRegistryService.deprecate(versionId));
    }

    @PostMapping("/versions/{versionId}/archive")
    @RequirePermission(WRITE)
    public R<ConnectorVersionResponse> archiveVersion(@PathVariable String versionId) {
        return R.ok(connectorRegistryService.archiveVersion(versionId));
    }

    @PostMapping("/{connectorId}/archive")
    @RequirePermission(WRITE)
    public R<Void> archiveConnector(@PathVariable String connectorId) {
        connectorRegistryService.archiveConnector(connectorId);
        return R.ok();
    }
}
