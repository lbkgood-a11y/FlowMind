package com.triobase.service.workflow.action;

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
import java.util.List;

@RestController
@RequestMapping("/internal/v1/actions")
@RequiredArgsConstructor
public class WorkflowActionOwnerController {

    private final WorkflowActionOwnerExecutionService executionService;

    @PostMapping("/execute")
    public R<ActionOwnerDispatchResponse> execute(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            WorkflowActionExecutionContext.set(request);
            return R.ok(executionService.execute(request));
        } finally {
            WorkflowActionExecutionContext.clear();
            SecurityContextHolder.clear();
            TraceUtil.clear();
        }
    }

    @PostMapping("/guard")
    public R<ActionOwnerGuardResponse> guard(@RequestBody ActionOwnerDispatchRequest request) {
        try {
            applyActionContext(request);
            WorkflowActionExecutionContext.set(request);
            return R.ok(executionService.guard(request));
        } finally {
            WorkflowActionExecutionContext.clear();
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
                permissions(request.getActionType()),
                request.getContext().getAuthVersion(),
                request.getContext().getRoleVersion(),
                request.getContext().getDataPolicyVersion(),
                request.getContext().getAuthorizationVersion(),
                request.getContext().getFieldPolicyVersion(),
                request.getContext().getGuardTemplateVersion()));
    }

    private List<String> permissions(String actionType) {
        return switch (actionType) {
            case "process.instance.start" -> List.of("/api/v1/process-instances/start:POST");
            case "process.task.approve" -> List.of("/api/v1/tasks/*/approve:POST");
            case "process.task.reject" -> List.of("/api/v1/tasks/*/reject:POST");
            case "process.task.transfer" -> List.of("/api/v1/tasks/*/transfer:POST");
            case "process.task.addSign" -> List.of("/api/v1/tasks/*/add-sign:POST");
            case "process.closure.effect.retry", "process.closure.effect.markHandled" ->
                    List.of("/api/v1/process-closures/*/retry:POST");
            default -> Collections.emptyList();
        };
    }
}
