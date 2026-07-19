package com.triobase.service.openapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.dto.CreateStructureRequest;
import com.triobase.service.openapi.dto.OpenApiExportRequest;
import com.triobase.service.openapi.dto.OpenApiImportHttpRequest;
import com.triobase.service.openapi.dto.OpenApiImportResult;
import com.triobase.service.openapi.dto.PublicationApproval;
import com.triobase.service.openapi.dto.StructureResponse;
import com.triobase.service.openapi.dto.StructureVersionMutationRequest;
import com.triobase.service.openapi.dto.StructureVersionResponse;
import com.triobase.service.openapi.service.OpenApiExportService;
import com.triobase.service.openapi.service.OpenApiImportService;
import com.triobase.service.openapi.service.StructureRegistryService;
import com.triobase.service.openapi.service.StructureVersionService;
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

@RestController
@RequestMapping("/api/v1/openapi/management")
@RequiredArgsConstructor
public class StructureManagementController {

    private static final String STRUCTURE_READ = "/api/v1/openapi/management/structures:GET";
    private static final String STRUCTURE_CREATE = "/api/v1/openapi/management/structures:POST";
    private static final String STRUCTURE_EDIT = "/api/v1/openapi/management/structures/*:PUT";
    private static final String STRUCTURE_PUBLISH = "/api/v1/openapi/management/structures/*/publish:POST";
    private static final String STRUCTURE_DEPRECATE = "/api/v1/openapi/management/structures/*/deprecate:POST";
    private static final String STRUCTURE_ARCHIVE = "/api/v1/openapi/management/structures/*/archive:POST";

    private final StructureRegistryService registryService;
    private final StructureVersionService versionService;
    private final OpenApiImportService importService;
    private final OpenApiExportService exportService;

    @PostMapping("/structures")
    @RequirePermission(STRUCTURE_CREATE)
    public R<StructureResponse> create(@Valid @RequestBody CreateStructureRequest request) {
        return R.ok(registryService.create(request));
    }

    @GetMapping("/structures")
    @RequirePermission(STRUCTURE_READ)
    public R<PageResult<StructureResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String kind) {
        return R.ok(registryService.list(page, size, namespace, kind));
    }

    @GetMapping("/structures/{structureId}")
    @RequirePermission(STRUCTURE_READ)
    public R<StructureResponse> get(@PathVariable String structureId) {
        return R.ok(registryService.getById(structureId));
    }

    @PostMapping("/structures/{structureId}/versions")
    @RequirePermission(STRUCTURE_CREATE)
    public R<StructureVersionResponse> createDraft(
            @PathVariable String structureId,
            @Valid @RequestBody StructureVersionMutationRequest request) {
        return R.ok(versionService.createDraft(
                structureId, request.schemaContent(), request.changeSummary()));
    }

    @PutMapping("/structures/versions/{versionId}")
    @RequirePermission(STRUCTURE_EDIT)
    public R<StructureVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody StructureVersionMutationRequest request) {
        return R.ok(versionService.updateDraft(
                versionId, request.schemaContent(), request.changeSummary(), request.semanticChange()));
    }

    @PostMapping("/structures/versions/{versionId}/publish")
    @RequirePermission(STRUCTURE_PUBLISH)
    public R<StructureVersionResponse> publish(
            @PathVariable String versionId,
            @RequestBody(required = false) PublicationApproval approval) {
        return R.ok(versionService.publish(
                versionId, approval == null ? PublicationApproval.none() : approval));
    }

    @PostMapping("/structures/versions/{versionId}/deprecate")
    @RequirePermission(STRUCTURE_DEPRECATE)
    public R<StructureVersionResponse> deprecate(@PathVariable String versionId) {
        return R.ok(versionService.deprecate(versionId));
    }

    @PostMapping("/structures/versions/{versionId}/archive")
    @RequirePermission(STRUCTURE_ARCHIVE)
    public R<StructureVersionResponse> archive(@PathVariable String versionId) {
        return R.ok(versionService.archive(versionId));
    }

    @PostMapping("/structures/imports/openapi")
    @RequirePermission(STRUCTURE_CREATE)
    public R<OpenApiImportResult> importOpenApi(
            @Valid @RequestBody OpenApiImportHttpRequest request) {
        return R.ok(importService.importAll(request.toServiceRequest()));
    }

    @PostMapping("/structures/exports/openapi")
    @RequirePermission(STRUCTURE_READ)
    public R<JsonNode> exportOpenApi(@Valid @RequestBody OpenApiExportRequest request) {
        return R.ok(exportService.export(request));
    }
}
