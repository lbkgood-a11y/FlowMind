package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.action.OpenApiActionDispatchService;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackSignalDispatcherTest {

    @Mock private CallbackInboxMapper inboxMapper;
    @Mock private OrchestrationRuntimeService runtimeService;
    @Mock private OpenApiActionDispatchService actionDispatchService;

    @Test
    void scheduledDispatcherSubmitsCallbackSignalActions() {
        CallbackInbox inbox = new CallbackInbox();
        inbox.setId("inbox-1");
        when(inboxMapper.findSignalPending(100)).thenReturn(List.of(inbox));
        CallbackSignalDispatcher dispatcher = new CallbackSignalDispatcher(
                inboxMapper, runtimeService, new ObjectMapper(), actionDispatchService);

        dispatcher.dispatchPending();

        verify(actionDispatchService).signalCallback("inbox-1");
    }

    @Test
    void ownerDispatchWorkerOutageKeepsDurableSignalPendingAndNextDispatchSucceeds() {
        CallbackInbox inbox = new CallbackInbox();
        inbox.setId("inbox-1");
        inbox.setExecutionId("execution-1");
        inbox.setSignalName("partner-result");
        inbox.setMappedPayload(new ObjectMapper().createObjectNode().put("status", "DONE"));
        inbox.setSignalAttempts(0);
        inbox.setInboxState(CallbackInboxState.SIGNAL_PENDING);
        when(inboxMapper.selectById("inbox-1")).thenReturn(inbox);
        when(inboxMapper.claimForSignal("inbox-1")).thenReturn(1);
        doThrow(new IllegalStateException("worker unavailable"))
                .doNothing().when(runtimeService).signal(any(), any());
        CallbackSignalDispatcher dispatcher = new CallbackSignalDispatcher(
                inboxMapper, runtimeService, new ObjectMapper(), actionDispatchService);

        dispatcher.dispatchInbox("inbox-1");
        assertThat(inbox.getInboxState()).isEqualTo(CallbackInboxState.SIGNAL_PENDING);
        assertThat(inbox.getSignalAttempts()).isEqualTo(1);
        assertThat(inbox.getLastSignalError()).isEqualTo("CALLBACK_SIGNAL_TEMPORARILY_UNAVAILABLE");

        dispatcher.dispatchInbox("inbox-1");
        assertThat(inbox.getInboxState()).isEqualTo(CallbackInboxState.SIGNALLED);
        assertThat(inbox.getSignalAttempts()).isEqualTo(2);
        verify(runtimeService, times(2)).signal(any(), any());
    }
}
