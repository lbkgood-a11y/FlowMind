package com.triobase.service.workflow.config;

import com.triobase.common.temporal.interceptor.TraceContextPropagator;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TemporalTraceConfiguration {

    @Bean
    public TraceContextPropagator traceContextPropagator() {
        return new TraceContextPropagator();
    }

    @Bean
    public TemporalOptionsCustomizer<WorkflowClientOptions.Builder> workflowClientOptionsCustomizer(
            TraceContextPropagator traceContextPropagator) {
        return builder -> builder.setContextPropagators(List.of(traceContextPropagator));
    }
}
