package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.ApplicationResponse;
import com.triobase.service.lowcode.dto.CreateApplicationRequest;
import com.triobase.service.lowcode.dto.UpdateApplicationRequest;
import com.triobase.service.lowcode.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lowcode-applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @RequirePermission("/api/v1/lowcode-applications:GET")
    public R<PageResult<ApplicationResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(applicationService.list(page, size));
    }

    @PostMapping
    @RequirePermission("/api/v1/lowcode-applications:POST")
    public R<ApplicationResponse> create(@RequestBody CreateApplicationRequest request,
                                         @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(applicationService.create(request, operator));
    }

    @GetMapping("/versions/{versionId}")
    @RequirePermission("/api/v1/lowcode-applications:GET")
    public R<ApplicationResponse> getVersion(@PathVariable String versionId) {
        return R.ok(applicationService.getVersion(versionId));
    }

    @PutMapping("/versions/{versionId}")
    @RequirePermission("/api/v1/lowcode-applications/*:PUT")
    public R<ApplicationResponse> update(@PathVariable String versionId,
                                         @RequestBody UpdateApplicationRequest request,
                                         @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(applicationService.update(versionId, request, operator));
    }

    @PostMapping("/versions/{versionId}/derive")
    @RequirePermission("/api/v1/lowcode-applications/*/versions:POST")
    public R<ApplicationResponse> deriveNewVersion(@PathVariable String versionId,
                                                   @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(applicationService.deriveNewVersion(versionId, operator));
    }

    @PutMapping("/versions/{versionId}/publish")
    @RequirePermission("/api/v1/lowcode-applications/*/publish:PUT")
    public R<ApplicationResponse> publish(@PathVariable String versionId) {
        return R.ok(applicationService.publish(versionId));
    }

    @PutMapping("/versions/{versionId}/offline")
    @RequirePermission("/api/v1/lowcode-applications/*/offline:PUT")
    public R<ApplicationResponse> offline(@PathVariable String versionId) {
        return R.ok(applicationService.offline(versionId));
    }
}
