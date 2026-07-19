package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
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

    @GetMapping("/reject-targets/{processInstanceId}")
    @RequirePermission("/api/v1/tasks/reject-targets/*:GET")
    public R<List<String>> getRejectTargets(@PathVariable String processInstanceId) {
        return R.ok(taskService.getRejectTargets(processInstanceId));
    }
}
