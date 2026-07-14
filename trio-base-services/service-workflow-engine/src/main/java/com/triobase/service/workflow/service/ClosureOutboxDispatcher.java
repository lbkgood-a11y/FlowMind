package com.triobase.service.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClosureOutboxDispatcher {

    private final ClosureEffectExecutionService closureEffectExecutionService;

    @Scheduled(fixedDelayString = "${workflow.business-closure.outbox-dispatch-delay-ms:5000}")
    public void dispatch() {
        int dispatched = closureEffectExecutionService.dispatchPendingOutbox(20);
        if (dispatched > 0) {
            log.debug("Dispatched closure outbox records: count={}", dispatched);
        }
    }
}
