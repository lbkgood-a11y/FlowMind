package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.CreateProcessPackageRequest;
import com.triobase.service.workflow.dto.ProcessPackageResponse;
import com.triobase.service.workflow.service.ProcessPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/process-packages")
@RequiredArgsConstructor
public class ProcessPackageController {

    private final ProcessPackageService processPackageService;

    @GetMapping
    @RequirePermission("/api/v1/process-packages:GET")
    public R<PageResult<ProcessPackageResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(processPackageService.list(page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<ProcessPackageResponse> getById(@PathVariable String id) {
        return R.ok(processPackageService.getById(id));
    }

    @GetMapping("/by-key/{processKey}")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<ProcessPackageResponse> getByKey(@PathVariable String processKey) {
        return R.ok(processPackageService.getByKey(processKey));
    }

    @PostMapping
    @RequirePermission("/api/v1/process-packages:POST")
    public R<ProcessPackageResponse> create(@RequestBody CreateProcessPackageRequest request) {
        return R.ok(processPackageService.create(request));
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("/api/v1/process-packages/*:PUT")
    public R<ProcessPackageResponse> publish(@PathVariable String id) {
        return R.ok(processPackageService.publish(id));
    }

    @PutMapping("/{id}/offline")
    @RequirePermission("/api/v1/process-packages/*:PUT")
    public R<ProcessPackageResponse> offline(@PathVariable String id) {
        return R.ok(processPackageService.offline(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/process-packages/*:DELETE")
    public R<Void> delete(@PathVariable String id) {
        processPackageService.delete(id);
        return R.ok();
    }
}
