package com.triobase.service.workflow.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.entity.ClosureEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClosureEffectOperationServiceTest {

    private final ClosureEffectExecutionService executionService =
            mock(ClosureEffectExecutionService.class);
    private final ClosureEffectOperationService service =
            new ClosureEffectOperationService(executionService, mock(ProcessClosureQueryService.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void retryRequiresRetryClosurePermission() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of(),
                null,
                null,
                null));

        BizException exception = assertThrows(BizException.class,
                () -> service.retry("EFF001"));

        assertEquals("CLOSURE_RETRY_PERMISSION_DENIED", exception.getMessage());
        verifyNoInteractions(executionService);
    }

    @Test
    void authorizedRetryExecutesEffectWithOriginalIdempotency() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-closures/*/retry:POST"),
                null,
                null,
                null));
        ClosureEffect effect = new ClosureEffect();
        effect.setId("EFF001");
        effect.setEffectKey("approved.notify");
        effect.setStatus("SUCCEEDED");
        effect.setIdempotencyKey("PI001:APPROVED:approved.notify");
        when(executionService.executeEffect("EFF001")).thenReturn(effect);

        var response = service.retry("EFF001");

        assertEquals("SUCCEEDED", response.getStatus());
        assertEquals("PI001:APPROVED:approved.notify", response.getIdempotencyKey());
        verify(executionService).executeEffect("EFF001");
    }

    @Test
    void authorizedManualHandlingMarksEffectHandled() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1",
                "Alice",
                "TENANT_A",
                List.of(),
                List.of("/api/v1/process-closures/*/retry:POST"),
                null,
                null,
                null));
        ClosureEffect effect = new ClosureEffect();
        effect.setId("EFF001");
        effect.setEffectKey("approved.notify");
        effect.setStatus("MANUALLY_HANDLED");
        effect.setBusinessActionCode("notifyApplicant");
        when(executionService.markManuallyHandled("EFF001", "已线下补偿", "user-1", null))
                .thenReturn(effect);

        var response = service.markHandled("EFF001", "已线下补偿");

        assertEquals("MANUALLY_HANDLED", response.getStatus());
        assertEquals("notifyApplicant", response.getBusinessActionCode());
        verify(executionService).markManuallyHandled("EFF001", "已线下补偿", "user-1", null);
    }
}
