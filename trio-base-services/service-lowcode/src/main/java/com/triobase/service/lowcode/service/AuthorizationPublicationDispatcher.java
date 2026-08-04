package com.triobase.service.lowcode.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发授权 Outbox 分发。
 *
 * <p>事务方法保留在被 Spring 代理的 AuthorizationPublicationService 上，调度器只负责触发；
 * 禁止把 dispatchPending 内联到本类，否则自调用会绕过事务和记录租约。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthorizationPublicationDispatcher {

    private final AuthorizationPublicationService publicationService;

    @Scheduled(fixedDelayString = "${triobase.lowcode.authorization-outbox-delay-ms:5000}")
    public void dispatch() {
        publicationService.dispatchPending(20);
    }
}
