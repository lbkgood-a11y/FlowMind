package com.triobase.platform.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRouteExposureTest {

    @Test
    void internalServiceEndpointsAreNotExposedByGatewayRoutes() throws Exception {
        String configuration = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(configuration.contains("/api/v1/process-packages"));
        assertTrue(configuration.contains("/api/v1/tasks/**"));
        assertFalse(configuration.contains("/internal/v1"));
    }
}
