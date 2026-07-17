package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.PolicyEnforcementStateMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@RequiredArgsConstructor
public class IntegrationOperationsMetricsBinder implements MeterBinder {

    private final AuditEventMapper auditMapper;
    private final IntegrationExecutionMapper executionMapper;
    private final ExecutionStepAttemptMapper attemptMapper;
    private final CallbackInboxMapper callbackMapper;
    private final PolicyEnforcementStateMapper enforcementMapper;

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "triobase.openapi.application.denials", () -> auditMapper.selectCount(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getOutcome, "DENIED")));
        gauge(registry, "triobase.openapi.policy.lag", () -> enforcementMapper.selectCount(
                new LambdaQueryWrapper<PolicyEnforcementState>().ne(
                        PolicyEnforcementState::getDriftState, "CURRENT")));
        gauge(registry, "triobase.openapi.quota.exhaustions", () -> auditMapper.selectCount(
                new LambdaQueryWrapper<AuditEvent>().like(AuditEvent::getReason, "QUOTA")));
        gauge(registry, "triobase.openapi.route.active", () -> executionMapper.selectCount(
                new LambdaQueryWrapper<IntegrationExecution>().in(IntegrationExecution::getExecutionState,
                        ExecutionState.ACCEPTED, ExecutionState.RUNNING,
                        ExecutionState.WAITING_CALLBACK, ExecutionState.COMPENSATING)));
        gauge(registry, "triobase.openapi.route.failures", () -> executionMapper.selectCount(
                new LambdaQueryWrapper<IntegrationExecution>().eq(
                        IntegrationExecution::getExecutionState, ExecutionState.FAILED)));
        gauge(registry, "triobase.openapi.mapping.failures", () -> attemptMapper.selectCount(
                new LambdaQueryWrapper<ExecutionStepAttempt>()
                        .eq(ExecutionStepAttempt::getStepType, "TRANSFORM")
                        .eq(ExecutionStepAttempt::getAttemptState, "FAILED")));
        gauge(registry, "triobase.openapi.partner.failures", () -> attemptMapper.selectCount(
                new LambdaQueryWrapper<ExecutionStepAttempt>()
                        .in(ExecutionStepAttempt::getStepType, "INVOKE", "HTTP_INVOKE")
                        .eq(ExecutionStepAttempt::getAttemptState, "FAILED")));
        gauge(registry, "triobase.openapi.callback.quarantine", () -> callbackMapper.selectCount(
                new LambdaQueryWrapper<CallbackInbox>().eq(
                        CallbackInbox::getInboxState, CallbackInboxState.QUARANTINED)));
        gauge(registry, "triobase.openapi.callback.backlog", () -> callbackMapper.selectCount(
                new LambdaQueryWrapper<CallbackInbox>().in(CallbackInbox::getInboxState,
                        CallbackInboxState.SIGNAL_PENDING, CallbackInboxState.SIGNALING)));
        gauge(registry, "triobase.openapi.callback.retries", () -> callbackMapper.selectCount(
                new LambdaQueryWrapper<CallbackInbox>().gt(CallbackInbox::getSignalAttempts, 1)));
        gauge(registry, "triobase.openapi.workflow.retries", () -> attemptMapper.selectCount(
                new LambdaQueryWrapper<ExecutionStepAttempt>().gt(
                        ExecutionStepAttempt::getAttemptNumber, 1)));
        gauge(registry, "triobase.openapi.compensation.failures", () -> attemptMapper.selectCount(
                new LambdaQueryWrapper<ExecutionStepAttempt>()
                        .eq(ExecutionStepAttempt::getStepType, "COMPENSATE")
                        .eq(ExecutionStepAttempt::getAttemptState, "FAILED")));
        gauge(registry, "triobase.openapi.release.rollbacks", () -> auditMapper.selectCount(
                new LambdaQueryWrapper<AuditEvent>().eq(
                        AuditEvent::getAction, "RELEASE_ROLLED_BACK")));
    }

    private void gauge(MeterRegistry registry, String name, LongSupplier supplier) {
        Gauge.builder(name, supplier, value -> safe(value)).register(registry);
    }

    private double safe(LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Exception unavailable) {
            return Double.NaN;
        }
    }
}
