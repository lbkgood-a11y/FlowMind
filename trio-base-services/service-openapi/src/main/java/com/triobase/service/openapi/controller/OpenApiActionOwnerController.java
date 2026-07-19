package com.triobase.service.openapi.controller;

import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.action.OpenApiActionExecutionContext;
import com.triobase.service.openapi.action.OpenApiActionOwnerExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/actions")
@RequiredArgsConstructor
public class OpenApiActionOwnerController {

    private final OpenApiActionOwnerExecutionService executionService;

    @PostMapping("/execute")
    public R<ActionOwnerDispatchResponse> execute(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            OpenApiActionExecutionContext.set(request);
            return R.ok(executionService.execute(request));
        } finally {
            OpenApiActionExecutionContext.clear();
            SecurityContextHolder.clear();
            TraceUtil.clear();
        }
    }

    @PostMapping("/guard")
    public R<ActionOwnerGuardResponse> guard(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            OpenApiActionExecutionContext.set(request);
            return R.ok(executionService.guard(request));
        } finally {
            OpenApiActionExecutionContext.clear();
            SecurityContextHolder.clear();
            TraceUtil.clear();
        }
    }

    private void applyActionContext(ActionOwnerDispatchRequest request) {
        if (request == null) {
            return;
        }
        if (request.getContext() != null) {
            TraceUtil.setTraceId(request.getContext().getTraceId());
        }
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                request.getActor() != null ? request.getActor().getId() : null,
                request.getActor() != null ? request.getActor().getDisplayName() : null,
                request.getContext() != null ? request.getContext().getTenantId()
                        : request.getActor() != null ? request.getActor().getTenantId() : null,
                List.of("OPENAPI_ACTION_OWNER"),
                List.of(),
                request.getContext() != null ? request.getContext().getAuthVersion() : null,
                request.getContext() != null ? request.getContext().getRoleVersion() : null,
                request.getContext() != null ? request.getContext().getDataPolicyVersion() : null));
    }
}
