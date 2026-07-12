package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.dto.CreateImportExportTaskRequest;
import com.triobase.service.ops.dto.UpdateTaskProgressRequest;
import com.triobase.service.ops.entity.OpsImportExportTask;
import com.triobase.service.ops.service.ImportExportTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/import-export-tasks")
@RequiredArgsConstructor
public class ImportExportTaskController {

    private final ImportExportTaskService taskService;

    @GetMapping
    @RequirePermission("/api/v1/import-export-tasks:GET")
    public R<PageResult<OpsImportExportTask>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String taskType,
                                                   @RequestParam(required = false) String businessType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String createdBy) {
        return R.ok(taskService.page(page, size, taskType, businessType, status, createdBy));
    }

    @GetMapping("/mine")
    public R<PageResult<OpsImportExportTask>> mine(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String status) {
        return R.ok(taskService.mine(page, size, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/import-export-tasks:GET")
    public R<OpsImportExportTask> detail(@PathVariable String id) {
        return R.ok(taskService.detail(id));
    }

    @PostMapping("/import")
    @RequirePermission("/api/v1/import-export-tasks/import:POST")
    public R<OpsImportExportTask> createImport(@Valid @RequestBody CreateImportExportTaskRequest request) {
        return R.ok(taskService.createImport(request));
    }

    @PostMapping("/export")
    @RequirePermission("/api/v1/import-export-tasks/export:POST")
    public R<OpsImportExportTask> createExport(@Valid @RequestBody CreateImportExportTaskRequest request) {
        return R.ok(taskService.createExport(request));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("/api/v1/import-export-tasks/*/cancel:POST")
    public R<OpsImportExportTask> cancel(@PathVariable String id) {
        return R.ok(taskService.cancel(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/import-export-tasks/*:PUT")
    public R<OpsImportExportTask> updateProgress(@PathVariable String id,
                                                 @RequestBody UpdateTaskProgressRequest request) {
        return R.ok(taskService.updateProgress(id, request));
    }

    @GetMapping("/{id}/result")
    @RequirePermission("/api/v1/import-export-tasks/*/result:GET")
    public R<OpsImportExportTask> result(@PathVariable String id) {
        return R.ok(taskService.result(id));
    }
}
