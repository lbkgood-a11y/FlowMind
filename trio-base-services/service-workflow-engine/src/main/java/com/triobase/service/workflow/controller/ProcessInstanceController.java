package com.triobase.service.workflow.controller;

import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/process-instances")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    @PostMapping("/start")
    public R<ProcessInstanceResponse> start(@RequestBody StartProcessRequest request) {
        return R.ok(processInstanceService.startProcess(request));
    }

    @GetMapping
    public R<PageResult<ProcessInstanceResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String status) {
        return R.ok(processInstanceService.list(page, size, status));
    }

    @GetMapping("/{id}")
    public R<ProcessInstanceResponse> getById(@PathVariable String id) {
        return R.ok(processInstanceService.getById(id));
    }
}
