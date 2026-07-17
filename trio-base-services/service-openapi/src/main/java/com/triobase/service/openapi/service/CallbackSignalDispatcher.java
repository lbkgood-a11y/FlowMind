package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackSignalDispatcher {
    private final CallbackInboxMapper inboxMapper;
    private final OrchestrationRuntimeService orchestrationRuntimeService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${triobase.openapi.callbacks.signal-dispatch-delay-ms:1000}")
    public void dispatchPending() {
        inboxMapper.resetStaleSignalClaims();
        for (CallbackInbox inbox : inboxMapper.findSignalPending(100)) {
            if (inboxMapper.claimForSignal(inbox.getId()) != 1) {
                continue;
            }
            try {
                ObjectNode signal = objectMapper.createObjectNode();
                signal.put("name", inbox.getSignalName());
                signal.put("inboxId", inbox.getId());
                signal.set("payload", inbox.getMappedPayload());
                orchestrationRuntimeService.signal(inbox.getExecutionId(), signal.toString());
                inbox.setInboxState(CallbackInboxState.SIGNALLED);
                inbox.setSignalAttempts(inbox.getSignalAttempts() + 1);
                inbox.setNextSignalAt(null);
                inbox.setLastSignalError(null);
                inbox.setUpdatedAt(LocalDateTime.now());
                inboxMapper.updateById(inbox);
            } catch (Exception failure) {
                int attempts = inbox.getSignalAttempts() + 1;
                inbox.setInboxState(CallbackInboxState.SIGNAL_PENDING);
                inbox.setSignalAttempts(attempts);
                inbox.setNextSignalAt(LocalDateTime.now().plusSeconds(
                        Math.min(300, 1L << Math.min(8, attempts))));
                inbox.setLastSignalError("CALLBACK_SIGNAL_TEMPORARILY_UNAVAILABLE");
                inbox.setUpdatedAt(LocalDateTime.now());
                inboxMapper.updateById(inbox);
                log.warn("Callback signal deferred inboxId={} attempt={}", inbox.getId(), attempts);
            }
        }
    }
}
