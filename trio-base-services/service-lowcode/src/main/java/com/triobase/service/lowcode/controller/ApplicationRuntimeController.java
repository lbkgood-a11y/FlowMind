package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.RuntimeActionRequest;
import com.triobase.service.lowcode.dto.RuntimeActionResponse;
import com.triobase.service.lowcode.dto.RuntimeApplicationDescriptorResponse;
import com.triobase.service.lowcode.dto.RuntimeApplicationSummaryResponse;
import com.triobase.service.lowcode.dto.RuntimeRetryWorkflowRequest;
import com.triobase.service.lowcode.service.ApplicationRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lowcode-runtime/apps")
@RequiredArgsConstructor
public class ApplicationRuntimeController {

    private final ApplicationRuntimeService applicationRuntimeService;

    @GetMapping
    @RequirePermission("/api/v1/lowcode-runtime/apps:GET")
    public R<PageResult<RuntimeApplicationSummaryResponse>> listAvailable(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(applicationRuntimeService.listAvailable(page, size));
    }

    @GetMapping("/{appKey}")
    @RequirePermission("/api/v1/lowcode-runtime/apps/*:GET")
    public R<RuntimeApplicationDescriptorResponse> descriptor(
            @PathVariable String appKey,
            @RequestParam(required = false) Integer version) {
        return R.ok(applicationRuntimeService.descriptor(appKey, version));
    }

    @GetMapping("/{appKey}/instances")
    @RequirePermission("/api/v1/lowcode-runtime/apps/*/instances:GET")
    public R<PageResult<FormInstanceResponse>> listInstances(
            @PathVariable String appKey,
            @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(applicationRuntimeService.listInstances(appKey, version, page, size));
    }

    @GetMapping("/{appKey}/instances/{instanceId}")
    @RequirePermission("/api/v1/lowcode-runtime/apps/*/instances/*:GET")
    public R<FormInstanceResponse> getInstance(
            @PathVariable String appKey,
            @PathVariable String instanceId,
            @RequestParam(required = false) Integer version) {
        return R.ok(applicationRuntimeService.getInstance(appKey, version, instanceId));
    }

    @PostMapping("/{appKey}/actions/{actionCode}")
    @RequirePermission("/api/v1/lowcode-runtime/apps/*/actions/*:POST")
    public R<RuntimeActionResponse> runAction(
            @PathVariable String appKey,
            @PathVariable String actionCode,
            @RequestParam(required = false) Integer version,
            @RequestBody RuntimeActionRequest request) {
        return R.ok(applicationRuntimeService.runAction(appKey, version, actionCode, request));
    }

    @PostMapping("/{appKey}/instances/{instanceId}/retry-workflow")
    @RequirePermission("/api/v1/lowcode-runtime/apps/*/instances/*/retry-workflow:POST")
    public R<RuntimeActionResponse> retryWorkflow(
            @PathVariable String appKey,
            @PathVariable String instanceId,
            @RequestParam(required = false) Integer version,
            @RequestBody(required = false) RuntimeRetryWorkflowRequest request) {
        return R.ok(applicationRuntimeService.retryWorkflow(appKey, version, instanceId, request));
    }
}
