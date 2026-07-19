package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.service.ProcessClosureQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-closures")
@RequiredArgsConstructor
public class ProcessClosureController {

    private final ProcessClosureQueryService processClosureQueryService;

    @GetMapping("/instances/{processInstanceId}")
    @RequirePermission("/api/v1/process-instances:GET")
    public R<ProcessClosureDetailResponse> getByProcessInstanceId(
            @PathVariable String processInstanceId) {
        return R.ok(processClosureQueryService.getByProcessInstanceId(processInstanceId));
    }

}
