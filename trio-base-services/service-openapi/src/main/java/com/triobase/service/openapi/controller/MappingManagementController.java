package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.entity.MappingContractTest;
import com.triobase.service.openapi.domain.entity.ValueMapVersion;
import com.triobase.service.openapi.dto.ContractTestRunResult;
import com.triobase.service.openapi.dto.CreateMappingDraftRequest;
import com.triobase.service.openapi.dto.CreateMappingSetRequest;
import com.triobase.service.openapi.dto.CreateValueMapRequest;
import com.triobase.service.openapi.dto.MappingPreviewRequest;
import com.triobase.service.openapi.dto.MappingPreviewResponse;
import com.triobase.service.openapi.dto.MappingVersionResponse;
import com.triobase.service.openapi.dto.SaveMappingContractTestRequest;
import com.triobase.service.openapi.dto.UpdateMappingRulesRequest;
import com.triobase.service.openapi.dto.ValueMapLookupRequest;
import com.triobase.service.openapi.dto.ValueMapVersionRequest;
import com.triobase.service.openapi.service.MappingContractTestService;
import com.triobase.service.openapi.service.MappingPreviewService;
import com.triobase.service.openapi.service.MappingRegistryService;
import com.triobase.service.openapi.service.ValueMapService;
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
@RequestMapping("/api/v1/openapi/management")
@RequiredArgsConstructor
public class MappingManagementController {

    private static final String MAPPING_READ = "/api/v1/openapi/management/mappings:GET";
    private static final String MAPPING_WRITE = "/api/v1/openapi/management/mappings:POST";

    private final MappingRegistryService mappingRegistryService;
    private final MappingPreviewService previewService;
    private final MappingContractTestService contractTestService;
    private final ValueMapService valueMapService;

    @PostMapping("/mappings")
    @RequirePermission(MAPPING_WRITE)
    public R<MappingVersionResponse> create(@Valid @RequestBody CreateMappingSetRequest request) {
        return R.ok(mappingRegistryService.create(request));
    }

    @GetMapping("/mappings/versions/{versionId}")
    @RequirePermission(MAPPING_READ)
    public R<MappingVersionResponse> getVersion(@PathVariable String versionId) {
        return R.ok(mappingRegistryService.getVersion(versionId));
    }

    @PostMapping("/mappings/{mappingSetId}/versions")
    @RequirePermission(MAPPING_WRITE)
    public R<MappingVersionResponse> createDraft(
            @PathVariable String mappingSetId,
            @Valid @RequestBody CreateMappingDraftRequest request) {
        return R.ok(mappingRegistryService.createDraft(
                mappingSetId, request.sourceStructureVersionId(),
                request.targetStructureVersionId(), request.rules()));
    }

    @PutMapping("/mappings/versions/{versionId}/rules")
    @RequirePermission(MAPPING_WRITE)
    public R<MappingVersionResponse> updateRules(
            @PathVariable String versionId,
            @Valid @RequestBody UpdateMappingRulesRequest request) {
        return R.ok(mappingRegistryService.updateDraft(versionId, request.rules()));
    }

    @PostMapping("/mappings/versions/{versionId}/preview")
    @RequirePermission(MAPPING_READ)
    public R<MappingPreviewResponse> preview(
            @PathVariable String versionId,
            @Valid @RequestBody MappingPreviewRequest request) {
        return R.ok(previewService.preview(versionId, request.payload()));
    }

    @PostMapping("/mappings/versions/{versionId}/contract-tests")
    @RequirePermission(MAPPING_WRITE)
    public R<MappingContractTest> saveContractTest(
            @PathVariable String versionId,
            @Valid @RequestBody SaveMappingContractTestRequest request) {
        return R.ok(contractTestService.save(versionId, request));
    }

    @PostMapping("/mappings/versions/{versionId}/publish")
    @RequirePermission(MAPPING_WRITE)
    public R<MappingVersionResponse> publish(@PathVariable String versionId) {
        return R.ok(mappingRegistryService.publish(versionId));
    }

    @PostMapping("/value-maps")
    @RequirePermission(MAPPING_WRITE)
    public R<ValueMapVersion> createValueMap(@Valid @RequestBody CreateValueMapRequest request) {
        return R.ok(valueMapService.create(request));
    }

    @PostMapping("/value-maps/{valueMapSetId}/versions")
    @RequirePermission(MAPPING_WRITE)
    public R<ValueMapVersion> createValueMapDraft(
            @PathVariable String valueMapSetId,
            @Valid @RequestBody ValueMapVersionRequest request) {
        return R.ok(valueMapService.createDraft(valueMapSetId, request));
    }

    @PutMapping("/value-maps/versions/{versionId}")
    @RequirePermission(MAPPING_WRITE)
    public R<ValueMapVersion> updateValueMapDraft(
            @PathVariable String versionId,
            @Valid @RequestBody ValueMapVersionRequest request) {
        return R.ok(valueMapService.updateDraft(versionId, request));
    }

    @PostMapping("/value-maps/versions/{versionId}/publish")
    @RequirePermission(MAPPING_WRITE)
    public R<ValueMapVersion> publishValueMap(@PathVariable String versionId) {
        return R.ok(valueMapService.publish(versionId));
    }

    @PostMapping("/value-maps/versions/{versionId}/lookup")
    @RequirePermission(MAPPING_READ)
    public R<String> lookupValue(
            @PathVariable String versionId,
            @Valid @RequestBody ValueMapLookupRequest request) {
        return R.ok(valueMapService.lookup(
                versionId, request.value(), request.canonicalToExternal()));
    }
}
