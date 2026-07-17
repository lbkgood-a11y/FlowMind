package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ExecutionDiagnostic;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.dto.CaptureExecutionDiagnosticRequest;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionDiagnosticMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticCaptureServiceTest {

    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private ExecutionDiagnosticMapper diagnosticMapper;
    @Mock private IntegrationAuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DiagnosticCaptureService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosticCaptureService(executionMapper, diagnosticMapper,
                new SensitiveDataRedactor(), auditService);
        ReflectionTestUtils.setField(service, "retentionDays", 7);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "operator", "Operator", "tenant-a", List.of(),
                List.of(DiagnosticCaptureService.PERMISSION), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void storesOnlyRedactedDiagnosticBodiesForAtMostSevenDays() throws Exception {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId("execution-1");
        execution.setTenantId("tenant-a");
        when(executionMapper.selectById("execution-1")).thenReturn(execution);
        when(diagnosticMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(diagnosticMapper.insert(any(ExecutionDiagnostic.class))).thenReturn(1);
        CaptureExecutionDiagnosticRequest request = new CaptureExecutionDiagnosticRequest(
                objectMapper.readTree("{\"token\":\"secret\",\"value\":1}"),
                objectMapper.readTree("{\"authorization\":\"Bearer hidden\"}"),
                objectMapper.createObjectNode());

        var response = service.capture("execution-1", request);

        ArgumentCaptor<ExecutionDiagnostic> captor = ArgumentCaptor.forClass(ExecutionDiagnostic.class);
        verify(diagnosticMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestPayload().path("token").asText())
                .isEqualTo("***REDACTED***");
        assertThat(captor.getValue().getResponsePayload().path("authorization").asText())
                .isEqualTo("***REDACTED***");
        assertThat(java.time.Duration.between(captor.getValue().getCreatedAt(), response.expiresAt()).toDays())
                .isLessThanOrEqualTo(7);
    }

    @Test
    void deniesDiagnosticCaptureWithoutExplicitPermission() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "operator", "Operator", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
        assertThatThrownBy(() -> service.capture("execution-1",
                new CaptureExecutionDiagnosticRequest(null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_DIAGNOSTIC_CAPTURE_DENIED");
    }
}
