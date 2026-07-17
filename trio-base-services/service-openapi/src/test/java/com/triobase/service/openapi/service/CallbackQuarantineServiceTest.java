package com.triobase.service.openapi.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.ResolveCallbackQuarantineRequest;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallbackQuarantineServiceTest {

    @Mock private CallbackInboxMapper inboxMapper;
    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private IntegrationAuditService auditService;
    private CallbackQuarantineService service;

    @BeforeEach
    void setUp() {
        service = new CallbackQuarantineService(inboxMapper, executionMapper, auditService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "operator-1", "Operator", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void operatorCanLinkUnknownCallbackToWaitingExecutionForRetry() {
        CallbackInbox inbox = new CallbackInbox();
        inbox.setId("inbox-1");
        inbox.setTenantId("tenant-a");
        inbox.setApplicationClientId("client-1");
        inbox.setInboxState(CallbackInboxState.QUARANTINED);
        when(inboxMapper.selectById("inbox-1")).thenReturn(inbox);
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId("execution-1");
        execution.setTenantId("tenant-a");
        execution.setApplicationClientId("client-1");
        execution.setExecutionState(ExecutionState.WAITING_CALLBACK);
        when(executionMapper.selectById("execution-1")).thenReturn(execution);
        when(inboxMapper.updateById(any(CallbackInbox.class))).thenReturn(1);

        CallbackInbox resolved = service.resolve("inbox-1",
                new ResolveCallbackQuarantineRequest("LINK", "execution-1", "verified manually"));

        assertThat(resolved.getInboxState()).isEqualTo(CallbackInboxState.SIGNAL_PENDING);
        assertThat(resolved.getExecutionId()).isEqualTo("execution-1");
        assertThat(resolved.getResolvedBy()).isEqualTo("operator-1");
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("CALLBACK_QUARANTINE_RESOLVED"),
                org.mockito.ArgumentMatchers.eq("CALLBACK_INBOX"),
                org.mockito.ArgumentMatchers.eq("inbox-1"), any());
    }
}
