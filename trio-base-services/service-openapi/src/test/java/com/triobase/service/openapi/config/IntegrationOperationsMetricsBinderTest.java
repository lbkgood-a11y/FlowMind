package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.PolicyEnforcementStateMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationOperationsMetricsBinderTest {

    @Mock private AuditEventMapper auditMapper;
    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private ExecutionStepAttemptMapper attemptMapper;
    @Mock private CallbackInboxMapper callbackMapper;
    @Mock private PolicyEnforcementStateMapper enforcementMapper;

    @Test
    void exposesOperationalMetricsRequiredByAlertRules() {
        when(auditMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(executionMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(callbackMapper.selectCount(any(Wrapper.class))).thenReturn(4L);
        when(enforcementMapper.selectCount(any(Wrapper.class))).thenReturn(5L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new IntegrationOperationsMetricsBinder(auditMapper, executionMapper, attemptMapper,
                callbackMapper, enforcementMapper).bindTo(registry);

        assertThat(registry.get("triobase.openapi.application.denials").gauge().value()).isEqualTo(1);
        assertThat(registry.get("triobase.openapi.route.active").gauge().value()).isEqualTo(2);
        assertThat(registry.get("triobase.openapi.policy.lag").gauge().value()).isEqualTo(5);
        assertThat(registry.get("triobase.openapi.callback.quarantine").gauge().value()).isEqualTo(4);
        assertThat(registry.get("triobase.openapi.compensation.failures").gauge().value()).isEqualTo(3);
        assertThat(registry.getMeters()).hasSizeGreaterThanOrEqualTo(13);
    }
}
