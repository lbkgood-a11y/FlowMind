package com.triobase.service.workflow.config;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import feign.Request;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class InternalFeignConfiguration {

    @Bean
    public Request.Options internalRequestOptions(WorkflowIntegrationProperties properties) {
        return new Request.Options(
                properties.getParticipants().getConnectTimeout(),
                properties.getParticipants().getReadTimeout(),
                false);
    }

    @Bean
    public Retryer internalRetryer() {
        return new Retryer.Default(50L, 100L, 2);
    }

    @Bean
    public RequestInterceptor internalRequestInterceptor(WorkflowIntegrationProperties properties) {
        return template -> {
            template.header(
                    InternalServiceTokenFilter.HEADER_SERVICE_NAME,
                    properties.getInternal().getServiceName());
            template.header(
                    InternalServiceTokenFilter.HEADER_SERVICE_TOKEN,
                    properties.getInternal().getToken());
            String traceId = TraceUtil.getTraceId();
            if (traceId != null && !traceId.isBlank()) {
                template.header(TraceUtil.TRACE_ID_KEY, traceId);
            }
        };
    }

    @Bean
    public ErrorDecoder internalErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            if (response.status() >= 500) {
                return new RetryableException(
                        response.status(),
                        "Internal service unavailable: " + methodKey,
                        response.request().httpMethod(),
                        null,
                        (Long) null,
                        response.request());
            }
            if (response.status() == 404) {
                return new BizException(40490, "INTERNAL_RESOURCE_NOT_FOUND");
            }
            if (response.status() == 401 || response.status() == 403) {
                return new BizException(50201, "INTERNAL_SERVICE_AUTH_FAILED");
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }
}
