package com.triobase.common.action.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateBatchRequest;
import com.triobase.common.action.model.ActionCandidateBatchValidationResult;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.owner.ActionOwnerExecutor;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.core.trace.TraceUtil;

import java.util.List;
import java.util.function.Supplier;

/**
 * Base class for controllers that host actions locally via
 * {@link OwnerHostedActionRuntime}. Handles all runtime wiring, security-context
 * propagation, and exception translation.
 * <p>
 * Subclasses declare their own {@code @GetMapping} / {@code @PostMapping}
 * endpoints and delegate to the corresponding {@code do*} method.
 *
 * @param <T> the service's {@link ActionOwnerExecutor} implementation
 */
public abstract class AbstractActionOwnerController<T extends ActionOwnerExecutor> {

    protected final OwnerHostedActionRuntime runtime;
    protected final T executionService;

    protected AbstractActionOwnerController(
            ObjectMapper objectMapper,
            List<ActionDefinitionProvider> providers,
            T executionService,
            OwnerActionAuditSink auditSink) {
        this.executionService = executionService;
        this.runtime = new OwnerHostedActionRuntime(
                new ActionDefinitionRegistry(providers),
                new ActionPayloadValidator(objectMapper),
                wrappedExecutor(executionService),
                this::guardedEvaluator,
                new InMemoryOwnerActionIdempotencyStore(),
                auditSink != null ? auditSink : new InMemoryOwnerActionAuditSink());
    }

    // -----------------------------------------------------------------------
    // Template method — subclasses MUST implement
    // -----------------------------------------------------------------------

    /**
     * Populate {@link SecurityContextHolder} and {@link TraceUtil} from the
     * action request so downstream business logic sees the correct actor,
     * tenant, permissions, and trace context.
     */
    protected abstract void applyActionContext(GlobalActionRequest request);

    // -----------------------------------------------------------------------
    // Optional hooks — override when the service has its own ThreadLocal context
    // -----------------------------------------------------------------------

    /**
     * Called after {@link #applyActionContext} but before action execution.
     * Default is no-op. Override to set a service-specific {@code ThreadLocal}.
     */
    protected void onBeforeAction(GlobalActionRequest request) {
    }

    /**
     * Called in the {@code finally} block after action execution completes.
     * Default is no-op. Override to clear a service-specific {@code ThreadLocal}.
     */
    protected void onAfterAction() {
    }

    /**
     * Evaluate whether a guard condition allows the action to proceed.
     * Default always allows. Override to delegate to the execution service.
     */
    protected ActionOwnerGuardResponse evaluateGuard(
            ActionDefinition definition, GlobalActionRequest request) {
        return ActionOwnerGuardResponse.allowed("OK");
    }

    // -----------------------------------------------------------------------
    // Delegation methods (called by subclass endpoints)
    // -----------------------------------------------------------------------

    protected R<List<ActionDefinition>> doDefinitions() {
        return action(() -> runtime.definitions());
    }

    protected R<ActionCandidateValidationResult> doValidate(ActionCandidate candidate) {
        return action(() -> runtime.validate(candidate));
    }

    protected R<ActionCandidateBatchValidationResult> doValidateBatch(
            ActionCandidateBatchRequest request) {
        return action(() -> {
            ActionCandidateBatchValidationResult result =
                    new ActionCandidateBatchValidationResult();
            result.setResults(runtime.validateBatch(
                    request != null ? request.getCandidates() : List.of()));
            return result;
        });
    }

    protected R<GlobalActionResult> doDispatchCandidate(ActionCandidate candidate) {
        return action(() -> runtime.dispatch(candidate));
    }

    protected R<GlobalActionResult> doDispatch(GlobalActionRequest request) {
        return action(() -> runtime.dispatch(request));
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private ActionOwnerExecutor wrappedExecutor(T delegate) {
        return new ActionOwnerExecutor() {
            @Override
            public String actionType() {
                return delegate.actionType();
            }

            @Override
            public GlobalActionResult execute(GlobalActionRequest request) {
                return withContext(request, () -> delegate.execute(request));
            }
        };
    }

    private ActionOwnerGuardResponse guardedEvaluator(
            ActionDefinition definition, GlobalActionRequest request) {
        return withContext(request, () -> evaluateGuard(definition, request));
    }

    private <V> V withContext(GlobalActionRequest request, Supplier<V> supplier) {
        SecurityContextHolder.SecurityContext previousSecurity =
                SecurityContextHolder.get();
        String previousTraceId = TraceUtil.getTraceId();
        try {
            applyActionContext(request);
            onBeforeAction(request);
            return supplier.get();
        } finally {
            onAfterAction();
            restoreSecurity(previousSecurity);
            restoreTrace(previousTraceId);
        }
    }

    private static void restoreSecurity(
            SecurityContextHolder.SecurityContext previous) {
        if (previous != null) {
            SecurityContextHolder.set(previous);
        } else {
            SecurityContextHolder.clear();
        }
    }

    private static void restoreTrace(String previousTraceId) {
        TraceUtil.clear();
        TraceUtil.setTraceId(previousTraceId);
    }

    private static <T> R<T> action(Supplier<T> supplier) {
        try {
            return R.ok(supplier.get());
        } catch (ActionRuntimeException exception) {
            return R.fail(exception.getCode(), exception.getMessage());
        }
    }
}
