package com.triobase.platform.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DataMaskingFilterTest {

    private final DataMaskingFilter filter = new DataMaskingFilter();

    @Test
    void rejectsSensitiveAgentPromptBeforeOrchestrator() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/agent/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"联系手机号13800138000\"}"));
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            invoked.set(true);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void forwardsSafeAgentPromptWithBodyIntact() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/agent/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"帮我申请明天一天事假\"}"));
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            invoked.set(true);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
