package com.triobase.service.workflow.config;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InternalFeignConfigurationTest {

    private final InternalFeignConfiguration configuration = new InternalFeignConfiguration();

    @AfterEach
    void clearTrace() {
        TraceUtil.clear();
    }

    @Test
    void interceptorAddsServiceCredentialsAndTraceId() {
        WorkflowIntegrationProperties properties = properties();
        TraceUtil.setTraceId("trace-001");
        RequestTemplate template = new RequestTemplate();

        configuration.internalRequestInterceptor(properties).apply(template);

        assertEquals("service-workflow-engine",
                template.headers().get(InternalServiceTokenFilter.HEADER_SERVICE_NAME).iterator().next());
        assertEquals("test-token",
                template.headers().get(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN).iterator().next());
        assertEquals("trace-001", template.headers().get(TraceUtil.TRACE_ID_KEY).iterator().next());
    }

    @Test
    void optionsUseConfiguredSubSecondTimeouts() {
        Request.Options options = configuration.internalRequestOptions(properties());

        assertEquals(200, options.connectTimeoutMillis());
        assertEquals(400, options.readTimeoutMillis());
    }

    @Test
    void errorDecoderRetriesServerErrorsAndMapsAuthErrors() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost/internal/v1/test",
                java.util.Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response unavailable = Response.builder()
                .request(request)
                .status(503)
                .reason("Unavailable")
                .headers(java.util.Map.of())
                .build();
        Response unauthorized = Response.builder()
                .request(request)
                .status(401)
                .reason("Unauthorized")
                .headers(java.util.Map.of())
                .build();

        assertInstanceOf(RetryableException.class,
                configuration.internalErrorDecoder().decode("test", unavailable));
        assertInstanceOf(BizException.class,
                configuration.internalErrorDecoder().decode("test", unauthorized));
    }

    private WorkflowIntegrationProperties properties() {
        WorkflowIntegrationProperties properties = new WorkflowIntegrationProperties();
        properties.getInternal().setServiceName("service-workflow-engine");
        properties.getInternal().setToken("test-token");
        properties.getParticipants().setConnectTimeout(Duration.ofMillis(200));
        properties.getParticipants().setReadTimeout(Duration.ofMillis(400));
        return properties;
    }
}
