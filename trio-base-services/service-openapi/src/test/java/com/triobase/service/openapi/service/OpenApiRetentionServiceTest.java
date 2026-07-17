package com.triobase.service.openapi.service;

import com.triobase.service.openapi.infrastructure.mapper.OpenApiRetentionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenApiRetentionServiceTest {

    @Mock private OpenApiRetentionMapper mapper;

    @Test
    void removesDiagnosticsAndReferencesBeforeExpiredExecutionMetadata() {
        when(mapper.deleteExpiredDiagnostics()).thenReturn(2);
        when(mapper.deleteExpiredCallbackNonces()).thenReturn(3);
        when(mapper.deleteExpiredCallbacks()).thenReturn(4);
        when(mapper.deleteExpiredIdempotencyRecords()).thenReturn(5);
        when(mapper.deleteExpiredExecutions()).thenReturn(6);

        var result = new OpenApiRetentionService(mapper).cleanup();

        assertThat(result.executions()).isEqualTo(6);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteExpiredDiagnostics();
        order.verify(mapper).deleteExpiredCallbackNonces();
        order.verify(mapper).deleteExpiredCallbacks();
        order.verify(mapper).deleteExpiredIdempotencyRecords();
        order.verify(mapper).deleteExpiredExecutions();
    }
}
