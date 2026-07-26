package com.triobase.service.lowcode.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateBatchRequest;
import com.triobase.common.action.model.ActionCandidateBatchValidationResult;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.action.runtime.AbstractActionOwnerController;
import com.triobase.common.action.runtime.ActionDefinitionProvider;
import com.triobase.common.action.runtime.ActionExecutionContext;
import com.triobase.common.action.runtime.OwnerActionAuditSink;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lowcode-runtime/actions")
public class LowcodeOwnerHostedActionController
        extends AbstractActionOwnerController<LowcodeActionOwnerExecutionService> {

    @Autowired
    public LowcodeOwnerHostedActionController(ObjectMapper objectMapper,
                                              List<ActionDefinitionProvider> providers,
                                              LowcodeActionOwnerExecutionService executionService,
                                              OwnerActionAuditSink auditSink) {
        super(objectMapper, providers, executionService, auditSink);
    }

    @Override
    protected void applyActionContext(GlobalActionRequest request) {
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

    @Override
    protected void onBeforeAction(GlobalActionRequest request) {
        ActionExecutionContext.set(request);
    }

    @Override
    protected void onAfterAction() {
        ActionExecutionContext.clear();
    }

    @Override
    protected ActionOwnerGuardResponse evaluateGuard(ActionDefinition definition,
                                                     GlobalActionRequest request) {
        return executionService.guard(request);
    }

    @GetMapping("/definitions")
    public R<List<ActionDefinition>> definitions() {
        return doDefinitions();
    }

    @PostMapping("/candidates/validate")
    public R<ActionCandidateValidationResult> validate(
            @RequestBody ActionCandidate candidate) {
        return doValidate(candidate);
    }

    @PostMapping("/candidates/batch-validate")
    public R<ActionCandidateBatchValidationResult> validateBatch(
            @RequestBody ActionCandidateBatchRequest request) {
        return doValidateBatch(request);
    }

    @PostMapping("/candidates/dispatch")
    public R<GlobalActionResult> dispatchCandidate(
            @RequestBody ActionCandidate candidate) {
        return doDispatchCandidate(candidate);
    }

    @PostMapping("/dispatch")
    public R<GlobalActionResult> dispatch(
            @RequestBody GlobalActionRequest request) {
        return doDispatch(request);
    }
}
