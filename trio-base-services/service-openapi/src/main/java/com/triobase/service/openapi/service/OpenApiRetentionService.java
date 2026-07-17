package com.triobase.service.openapi.service;

import com.triobase.service.openapi.infrastructure.mapper.OpenApiRetentionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiRetentionService {

    private final OpenApiRetentionMapper retentionMapper;

    @Scheduled(cron = "${triobase.openapi.retention.cleanup-cron:0 15 2 * * *}")
    @Transactional
    public CleanupResult cleanup() {
        int diagnostics = retentionMapper.deleteExpiredDiagnostics();
        int nonces = retentionMapper.deleteExpiredCallbackNonces();
        int callbacks = retentionMapper.deleteExpiredCallbacks();
        int idempotency = retentionMapper.deleteExpiredIdempotencyRecords();
        int executions = retentionMapper.deleteExpiredExecutions();
        CleanupResult result = new CleanupResult(
                diagnostics, nonces, callbacks, idempotency, executions);
        log.info("OpenAPI retention cleanup diagnostics={} nonces={} callbacks={} idempotency={} executions={}",
                diagnostics, nonces, callbacks, idempotency, executions);
        return result;
    }

    public record CleanupResult(
            int diagnostics,
            int callbackNonces,
            int callbacks,
            int idempotencyRecords,
            int executions) {
    }
}
