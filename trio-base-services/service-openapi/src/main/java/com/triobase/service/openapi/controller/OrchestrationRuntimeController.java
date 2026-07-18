package com.triobase.service.openapi.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CancelOrchestrationRequest;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.dto.StartOrchestrationRequest;
import com.triobase.service.openapi.service.RuntimeAdmissionContextResolver;
import com.triobase.service.openapi.service.OrchestrationRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/runtime")
@RequiredArgsConstructor
public class OrchestrationRuntimeController {

    private final OrchestrationRuntimeService service;
    private final RuntimeAdmissionContextResolver admissionContextResolver;

    @PostMapping("/{routeKey}/orchestrations")
    public R<OrchestrationExecutionResponse> start(
            @PathVariable String routeKey,
            @RequestHeader("X-Environment") Environment environment,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StartOrchestrationRequest body,
            HttpServletRequest request) {
        RuntimeAdmissionContext admission = admissionContextResolver.resolve(
                request, routeKey, environment, request.getMethod());
        return R.ok(service.start(routeKey, environment, admission,
                request.getMethod(), idempotencyKey, body.payload()));
    }

    @GetMapping("/orchestrations/{executionId}")
    public R<OrchestrationExecutionResponse> status(
            @PathVariable String executionId,
            @RequestHeader("X-Application-Client-Id") String clientId) {
        return R.ok(service.status(executionId, clientId));
    }

    @GetMapping("/orchestrations/{executionId}/result")
    public R<OrchestrationExecutionResponse> result(
            @PathVariable String executionId,
            @RequestHeader("X-Application-Client-Id") String clientId) {
        return R.ok(service.result(executionId, clientId));
    }

    @PostMapping("/orchestrations/{executionId}/cancel")
    public R<OrchestrationExecutionResponse> cancel(
            @PathVariable String executionId,
            @RequestHeader("X-Application-Client-Id") String clientId,
            @Valid @RequestBody CancelOrchestrationRequest request) {
        return R.ok(service.cancel(executionId, clientId, request.reason()));
    }
}
