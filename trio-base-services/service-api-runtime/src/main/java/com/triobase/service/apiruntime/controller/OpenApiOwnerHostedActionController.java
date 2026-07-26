package com.triobase.service.apiruntime.controller;

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
import com.triobase.service.apiruntime.action.OpenApiActionOwnerExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openapi/management/actions")
public class OpenApiOwnerHostedActionController
        extends AbstractActionOwnerController<OpenApiActionOwnerExecutionService> {

    @Autowired
    public OpenApiOwnerHostedActionController(ObjectMapper objectMapper,
                                              List<ActionDefinitionProvider> providers,
                                              OpenApiActionOwnerExecutionService executionService,
                                              OwnerActionAuditSink auditSink) {
        super(objectMapper, providers, executionService, auditSink);
    }

    @Override
    protected void applyActionContext(GlobalActionRequest request) {
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
