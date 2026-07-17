package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.ExecutionDetailResponse;
import com.triobase.service.openapi.dto.ExecutionSummaryResponse;
import com.triobase.service.openapi.dto.CaptureExecutionDiagnosticRequest;
import com.triobase.service.openapi.dto.DiagnosticCaptureResponse;
import com.triobase.service.openapi.service.DiagnosticCaptureService;
import com.triobase.service.openapi.service.ExecutionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/openapi/management/executions")
@RequiredArgsConstructor
public class ExecutionOperationsController {

    private static final String READ = "/api/v1/openapi/management/executions:GET";
    private final ExecutionQueryService service;
    private final DiagnosticCaptureService diagnosticCaptureService;

    @GetMapping
    @RequirePermission(READ)
    public R<PageResult<ExecutionSummaryResponse>> search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String applicationClientId,
            @RequestParam(required = false) String routeDefinitionId,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) ExecutionState state,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedUntil) {
        return R.ok(service.search(page, size, applicationClientId, routeDefinitionId,
                environment, state, traceId, startedFrom, startedUntil));
    }

    @GetMapping("/{executionId}")
    @RequirePermission(READ)
    public R<ExecutionDetailResponse> get(@PathVariable String executionId) {
        return R.ok(service.get(executionId));
    }

    @org.springframework.web.bind.annotation.PostMapping("/{executionId}/diagnostics")
    @RequirePermission(DiagnosticCaptureService.PERMISSION)
    public R<DiagnosticCaptureResponse> captureDiagnostic(
            @PathVariable String executionId,
            @org.springframework.web.bind.annotation.RequestBody CaptureExecutionDiagnosticRequest request) {
        return R.ok(diagnosticCaptureService.capture(executionId, request));
    }
}
