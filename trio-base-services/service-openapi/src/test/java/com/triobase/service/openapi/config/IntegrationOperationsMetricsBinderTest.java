package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.triobase.common.openapi.entity.CallbackInbox;
import com.triobase.common.openapi.entity.ExecutionStepAttempt;
import com.triobase.common.openapi.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.PolicyEnforcementStateMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AuditEventMapper.class.getName(), AuditEvent.class);
        initTableInfo(IntegrationExecutionMapper.class.getName(), IntegrationExecution.class);
        initTableInfo(ExecutionStepAttemptMapper.class.getName(), ExecutionStepAttempt.class);
        initTableInfo(CallbackInboxMapper.class.getName(), CallbackInbox.class);
        initTableInfo(PolicyEnforcementStateMapper.class.getName(), PolicyEnforcementState.class);
    }

    @Test
    void exposesOperationalMetricsRequiredByAlertRules() {
        when(auditMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(executionMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(callbackMapper.selectCount(any(Wrapper.class))).thenReturn(4L);
        when(enforcementMapper.selectCount(any(Wrapper.class))).thenReturn(5L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        IntegrationOperationsMetricsBinder binder = new IntegrationOperationsMetricsBinder(
                auditMapper, executionMapper, attemptMapper, callbackMapper, enforcementMapper);
        binder.bindTo(registry);

        assertThat(registry.get("triobase.openapi.application.denials").gauge().value()).isEqualTo(1);
        assertThat(registry.get("triobase.openapi.route.active").gauge().value()).isEqualTo(2);
        assertThat(registry.get("triobase.openapi.policy.lag").gauge().value()).isEqualTo(5);
        assertThat(registry.get("triobase.openapi.callback.quarantine").gauge().value()).isEqualTo(4);
        assertThat(registry.get("triobase.openapi.compensation.failures").gauge().value()).isEqualTo(3);
        assertThat(registry.getMeters()).hasSizeGreaterThanOrEqualTo(13);
    }

    private static void initTableInfo(String namespace, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
