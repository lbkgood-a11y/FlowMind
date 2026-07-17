package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.dto.LifecycleAssetItem;
import com.triobase.service.openapi.dto.LifecycleReadinessResponse;
import com.triobase.service.openapi.service.OpenApiLifecycleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/management/operations")
@RequiredArgsConstructor
public class OpenApiLifecycleOperationsController {
    public static final String READ = "/api/v1/openapi/management/operations:GET";
    private final OpenApiLifecycleCatalogService service;

    @GetMapping("/assets/{assetType}")
    @RequirePermission(READ)
    public R<PageResult<LifecycleAssetItem>> assets(@PathVariable String assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.search(assetType, keyword, state, page, size));
    }

    @GetMapping("/readiness")
    @RequirePermission(READ)
    public R<LifecycleReadinessResponse> readiness() {
        return R.ok(service.readiness());
    }
}
