package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.ManualClosureEffectRequest;
import com.triobase.service.workflow.dto.ProcessClosureDetailResponse;
import com.triobase.service.workflow.service.ClosureEffectOperationService;
import com.triobase.service.workflow.service.ProcessClosureQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process-closures")
@RequiredArgsConstructor
public class ProcessClosureController {

    private final ProcessClosureQueryService processClosureQueryService;
    private final ClosureEffectOperationService closureEffectOperationService;

    @GetMapping("/instances/{processInstanceId}")
    @RequirePermission("/api/v1/process-instances:GET")
    public R<ProcessClosureDetailResponse> getByProcessInstanceId(
            @PathVariable String processInstanceId) {
        return R.ok(processClosureQueryService.getByProcessInstanceId(processInstanceId));
    }

    @PostMapping("/effects/{effectId}/retry")
    @RequirePermission("/api/v1/process-closures/*/retry:POST")
    public R<ProcessClosureDetailResponse.EffectItem> retry(@PathVariable String effectId) {
        return R.ok(closureEffectOperationService.retry(effectId));
    }

    @PostMapping("/effects/{effectId}/manual-handled")
    @RequirePermission("/api/v1/process-closures/*/retry:POST")
    public R<ProcessClosureDetailResponse.EffectItem> markHandled(
            @PathVariable String effectId,
            @RequestBody(required = false) ManualClosureEffectRequest request) {
        return R.ok(closureEffectOperationService.markHandled(
                effectId,
                request == null ? null : request.getReason()));
    }
}
