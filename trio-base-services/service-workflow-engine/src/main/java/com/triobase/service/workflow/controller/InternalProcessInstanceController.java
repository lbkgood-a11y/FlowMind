package com.triobase.service.workflow.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.ProcessInstanceResponse;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/process-instances")
@RequiredArgsConstructor
public class InternalProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    @PostMapping("/start")
    public R<ProcessInstanceResponse> start(@RequestBody StartProcessRequest request) {
        return R.ok(processInstanceService.startProcess(request));
    }
}
