package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.AddSignRequest;
import com.triobase.service.workflow.dto.ApproveTaskRequest;
import com.triobase.service.workflow.dto.RejectTaskRequest;
import com.triobase.service.workflow.dto.TransferTaskRequest;
import com.triobase.service.workflow.dto.TaskResponse;
import com.triobase.service.workflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/my-pending")
    @RequirePermission("/api/v1/tasks/my-pending:GET")
    public R<PageResult<TaskResponse>> myPendingTasks(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(taskService.myPendingTasks(page, size));
    }

    @GetMapping("/my-completed")
    @RequirePermission("/api/v1/tasks/my-completed:GET")
    public R<PageResult<TaskResponse>> myCompletedTasks(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(taskService.myCompletedTasks(page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/tasks/*:GET")
    public R<TaskResponse> getById(@PathVariable String id) {
        return R.ok(taskService.getById(id));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("/api/v1/tasks/*/approve:POST")
    public R<TaskResponse> approve(@PathVariable String id, @RequestBody ApproveTaskRequest request) {
        return R.ok(taskService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("/api/v1/tasks/*/reject:POST")
    public R<TaskResponse> reject(@PathVariable String id, @RequestBody RejectTaskRequest request) {
        return R.ok(taskService.reject(id, request));
    }

    @PostMapping("/{id}/transfer")
    @RequirePermission("/api/v1/tasks/*/transfer:POST")
    public R<TaskResponse> transfer(@PathVariable String id, @RequestBody TransferTaskRequest request) {
        return R.ok(taskService.transfer(id, request));
    }

    @PostMapping("/{id}/add-sign")
    @RequirePermission("/api/v1/tasks/*/add-sign:POST")
    public R<TaskResponse> addSign(@PathVariable String id, @RequestBody AddSignRequest request) {
        return R.ok(taskService.addSign(id, request));
    }

    @GetMapping("/reject-targets/{processInstanceId}")
    @RequirePermission("/api/v1/tasks/reject-targets/*:GET")
    public R<List<String>> getRejectTargets(@PathVariable String processInstanceId) {
        return R.ok(taskService.getRejectTargets(processInstanceId));
    }
}
