package com.triobase.service.openapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import com.triobase.service.openapi.dto.SyncInvocationResponse;
import com.triobase.service.openapi.service.RuntimeAdmissionContextResolver;
import com.triobase.service.openapi.service.SynchronousInvocationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/runtime")
@RequiredArgsConstructor
public class SynchronousRuntimeController {

    private final SynchronousInvocationService service;
    private final RuntimeAdmissionContextResolver admissionContextResolver;

    @RequestMapping(
            value = "/{routeKey}",
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.PATCH,
                    RequestMethod.DELETE
            })
    public R<SyncInvocationResponse> invoke(
            @PathVariable String routeKey,
            @RequestHeader("X-Environment") Environment environment,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        RuntimeAdmissionContext admission = admissionContextResolver.resolve(
                request, routeKey, environment, request.getMethod());
        return R.ok(service.invoke(routeKey, environment, admission, request.getMethod(), body));
    }
}
