package com.triobase.platform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 数据脱敏过滤器 — 铁律 2 第一道防线。
 * 拦截发往 LLM 服务的请求，扫描请求体中是否包含明文敏感数据。
 * 若检测到敏感字段，直接拒绝请求 (403)，不允许绕开 LLM 网关直接请求大模型。
 */
@Component
public class DataMaskingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DataMaskingFilter.class);

    private static final Set<String> LLM_PATHS = Set.of("/api/v1/ai/", "/api/v1/llm/");
    private static final Set<HttpMethod> METHODS_WITH_BODY = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\d{16,19}");
    private static final Pattern FINANCE_KEY_PATTERN = Pattern.compile(
            "(secret|SECRET|api_key|API_KEY|private_key|PRIVATE_KEY|access_token|ACCESS_TOKEN)\\s*[:=]\\s*[\"']?[a-zA-Z0-9_-]{20,}");
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "\"(secretKey|apiKey|privateKey|password|token|密钥|秘钥|密码|令牌)\"\\s*:\\s*\"[^\"]{8,}\"");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean isLLMPath = LLM_PATHS.stream().anyMatch(path::startsWith);

        if (!isLLMPath || !hasInspectableBody(exchange)) {
            return chain.filter(exchange);
        }

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(bufferFactory.wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bodyBytes.length == 0) {
                        return chain.filter(exchange);
                    }

                    String body = new String(bodyBytes, StandardCharsets.UTF_8);
                    List<String> findings = detectFindings(body);
                    if (!findings.isEmpty()) {
                        log.warn("Sensitive data detected on path {} findings={}", path, findings);
                        return reject(exchange, findings);
                    }

                    ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(super.getHeaders());
                            headers.setContentLength(bodyBytes.length);
                            return headers;
                        }

                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> Flux.just(bufferFactory.wrap(bodyBytes)));
                        }
                    };

                    return chain.filter(exchange.mutate().request(decoratedRequest).build());
                });
    }

    private boolean hasInspectableBody(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null || !METHODS_WITH_BODY.contains(method)) {
            return false;
        }
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        return contentType == null
                || MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || MediaType.TEXT_PLAIN.isCompatibleWith(contentType);
    }

    private List<String> detectFindings(String body) {
        List<String> findings = new ArrayList<>();
        if (PHONE_PATTERN.matcher(body).find()) {
            findings.add("phone_number");
        }
        if (ID_CARD_PATTERN.matcher(body).find()) {
            findings.add("id_card");
        }
        if (BANK_CARD_PATTERN.matcher(body).find()) {
            findings.add("bank_card");
        }
        if (FINANCE_KEY_PATTERN.matcher(body).find()) {
            findings.add("financial_key");
        }
        if (SENSITIVE_FIELD_PATTERN.matcher(body).find()) {
            findings.add("sensitive_field");
        }
        return findings;
    }

    private Mono<Void> reject(ServerWebExchange exchange, List<String> findings) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String payload = """
                {"code":40301,"message":"SENSITIVE_DATA_DETECTED","findings":%s}
                """.formatted(toJsonArray(findings));
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(payload.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String toJsonArray(List<String> findings) {
        return findings.stream()
                .map(item -> "\"" + item + "\"")
                .reduce((left, right) -> left + "," + right)
                .map(items -> "[" + items + "]")
                .orElse("[]");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
