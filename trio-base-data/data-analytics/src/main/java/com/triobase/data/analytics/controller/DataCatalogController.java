package com.triobase.data.analytics.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.data.analytics.dto.CreateDatasetRequest;
import com.triobase.data.analytics.dto.DatasetResponse;
import com.triobase.data.analytics.service.DatasetCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/datasets")
@RequiredArgsConstructor
public class DataCatalogController {

    private final DatasetCatalogService catalogService;

    @PostMapping
    @RequirePermission("/api/v1/data/datasets:POST")
    public R<DatasetResponse> create(@Valid @RequestBody CreateDatasetRequest request,
                                     @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(catalogService.create(request, operator));
    }

    @GetMapping
    @RequirePermission("/api/v1/data/datasets:GET")
    public R<PageResult<DatasetResponse>> list(@RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return R.ok(catalogService.list(status, page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/data/datasets:GET")
    public R<DatasetResponse> getById(@PathVariable String id) {
        return R.ok(catalogService.getById(id));
    }
}
