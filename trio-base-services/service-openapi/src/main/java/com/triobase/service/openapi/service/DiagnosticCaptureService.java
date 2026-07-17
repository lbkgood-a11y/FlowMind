package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ExecutionDiagnostic;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.dto.CaptureExecutionDiagnosticRequest;
import com.triobase.service.openapi.dto.DiagnosticCaptureResponse;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionDiagnosticMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DiagnosticCaptureService {

    public static final String PERMISSION =
            "/api/v1/openapi/management/executions/diagnostics:POST";
    private final IntegrationExecutionMapper executionMapper;
    private final ExecutionDiagnosticMapper diagnosticMapper;
    private final SensitiveDataRedactor redactor;
    private final IntegrationAuditService auditService;
    @Value("${triobase.openapi.retention.diagnostic-body-days:7}")
    private int retentionDays;

    @Transactional
    public DiagnosticCaptureResponse capture(
            String executionId, CaptureExecutionDiagnosticRequest request) {
        if (!SecurityContextHolder.getPermissions().contains(PERMISSION)) {
            throw new BizException(40380, "OPENAPI_DIAGNOSTIC_CAPTURE_DENIED");
        }
        IntegrationExecution execution = executionMapper.selectById(executionId);
        String tenantId = SecurityContextHolder.getTenantId();
        if (execution == null || (tenantId != null && !tenantId.equals(execution.getTenantId()))) {
            throw new BizException(40460, "OPENAPI_EXECUTION_NOT_FOUND");
        }
        JsonNode sanitizedRequest = redactor.payload(request.requestPayload(), request.redactionPolicy());
        JsonNode sanitizedResponse = redactor.payload(request.responsePayload(), request.redactionPolicy());
        requireBounded(sanitizedRequest);
        requireBounded(sanitizedResponse);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(Math.min(7, Math.max(1, retentionDays)));
        ExecutionDiagnostic diagnostic = diagnosticMapper.selectOne(
                new LambdaQueryWrapper<ExecutionDiagnostic>()
                        .eq(ExecutionDiagnostic::getExecutionId, executionId));
        boolean created = diagnostic == null;
        if (diagnostic == null) {
            diagnostic = new ExecutionDiagnostic();
            diagnostic.setId(UlidGenerator.nextUlid());
            diagnostic.setExecutionId(executionId);
            diagnostic.setCreatedAt(now);
        }
        diagnostic.setRequestPayload(sanitizedRequest);
        diagnostic.setResponsePayload(sanitizedResponse);
        diagnostic.setCapturedBy(operator());
        diagnostic.setExpiresAt(expires);
        if (created) {
            diagnosticMapper.insert(diagnostic);
        } else {
            diagnosticMapper.updateById(diagnostic);
        }
        execution.setDiagnosticEnabled(true);
        execution.setDiagnosticExpiresAt(expires);
        execution.setUpdatedAt(now);
        executionMapper.updateById(execution);
        auditService.success("EXECUTION_DIAGNOSTIC_CAPTURED", "EXECUTION", executionId,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("expiresAt", expires.toString()));
        return new DiagnosticCaptureResponse(diagnostic.getId(), executionId, expires);
    }

    private void requireBounded(JsonNode payload) {
        if (payload != null && payload.toString().length() > 1_048_576) {
            throw new BizException(41380, "OPENAPI_DIAGNOSTIC_PAYLOAD_TOO_LARGE");
        }
    }

    private String operator() {
        String user = SecurityContextHolder.getUserId();
        return user == null || user.isBlank() ? "SYSTEM" : user;
    }
}
