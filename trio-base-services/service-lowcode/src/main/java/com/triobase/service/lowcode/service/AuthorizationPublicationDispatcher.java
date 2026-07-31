package com.triobase.service.lowcode.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the transactional dispatch boundary on the proxied service. */
@Component
@RequiredArgsConstructor
public class AuthorizationPublicationDispatcher {

    private final AuthorizationPublicationService publicationService;

    @Scheduled(fixedDelayString = "${triobase.lowcode.authorization-outbox-delay-ms:5000}")
    public void dispatch() {
        publicationService.dispatchPending(20);
    }
}
