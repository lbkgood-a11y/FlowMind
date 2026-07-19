package com.triobase.service.lowcode.action;

import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.action.owner.ActionOwnerDispatchResponse;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/internal/v1/actions")
@RequiredArgsConstructor
public class LowcodeActionOwnerController {

    private final LowcodeActionOwnerExecutionService executionService;

    @PostMapping("/execute")
    public R<ActionOwnerDispatchResponse> execute(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            LowcodeActionExecutionContext.set(request);
            return R.ok(executionService.execute(request));
        } finally {
            LowcodeActionExecutionContext.clear();
            SecurityContextHolder.clear();
            TraceUtil.clear();
        }
    }

    @PostMapping("/guard")
    public R<ActionOwnerGuardResponse> guard(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            LowcodeActionExecutionContext.set(request);
            return R.ok(executionService.guard(request));
        } finally {
            LowcodeActionExecutionContext.clear();
            SecurityContextHolder.clear();
            TraceUtil.clear();
        }
    }

    private void applyActionContext(ActionOwnerDispatchRequest request) {
        if (request == null || request.getActor() == null || request.getContext() == null) {
            return;
        }
        TraceUtil.setTraceId(request.getContext().getTraceId());
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                request.getActor().getId(),
                request.getActor().getDisplayName(),
                request.getContext().getTenantId(),
                Collections.emptyList(),
                Collections.emptyList(),
                request.getContext().getAuthVersion(),
                request.getContext().getRoleVersion(),
                request.getContext().getDataPolicyVersion(),
                request.getContext().getAuthorizationVersion(),
                request.getContext().getFieldPolicyVersion(),
                request.getContext().getGuardTemplateVersion()));
    }
}
