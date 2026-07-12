package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.dto.SaveJobRequest;
import com.triobase.service.ops.entity.OpsJobDefinition;
import com.triobase.service.ops.entity.OpsJobExecutionLog;
import com.triobase.service.ops.service.JobSchedulerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobSchedulerController {

    private final JobSchedulerService jobSchedulerService;

    @GetMapping
    @RequirePermission("/api/v1/jobs:GET")
    public R<PageResult<OpsJobDefinition>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Short enabled) {
        return R.ok(jobSchedulerService.page(page, size, keyword, enabled));
    }

    @PostMapping
    @RequirePermission("/api/v1/jobs:POST")
    public R<OpsJobDefinition> create(@Valid @RequestBody SaveJobRequest request) {
        return R.ok(jobSchedulerService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/jobs/*:PUT")
    public R<OpsJobDefinition> update(@PathVariable String id, @Valid @RequestBody SaveJobRequest request) {
        return R.ok(jobSchedulerService.update(id, request));
    }

    @PutMapping("/{id}/enabled")
    @RequirePermission("/api/v1/jobs/*:PUT")
    public R<OpsJobDefinition> updateEnabled(@PathVariable String id, @RequestParam Short enabled) {
        return R.ok(jobSchedulerService.updateEnabled(id, enabled));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/jobs/*:DELETE")
    public R<Void> delete(@PathVariable String id) {
        jobSchedulerService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/trigger")
    @RequirePermission("/api/v1/jobs/*/trigger:POST")
    public R<OpsJobExecutionLog> trigger(@PathVariable String id) {
        return R.ok(jobSchedulerService.trigger(id));
    }

    @GetMapping("/{id}/logs")
    @RequirePermission("/api/v1/jobs/*/logs:GET")
    public R<PageResult<OpsJobExecutionLog>> logs(@PathVariable String id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String status) {
        return R.ok(jobSchedulerService.logs(id, page, size, status));
    }
}
