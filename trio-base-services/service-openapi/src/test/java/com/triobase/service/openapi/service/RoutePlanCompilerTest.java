package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePlanCompilerTest {

    @Test
    void compilesSameRouteToStablePlanAndHash() {
        ObjectMapper objectMapper = new ObjectMapper();
        RoutePlanCompiler compiler = new RoutePlanCompiler(objectMapper);
        RouteVersion route = new RouteVersion();
        route.setId("route-v1");
        route.setEnvironment(Environment.PROD);
        route.setPriority(10);
        route.setEnabled(true);
        route.setRoutePredicate(objectMapper.createObjectNode());
        route.setExecutionMode(ExecutionMode.SYNCHRONOUS);
        route.setConnectorVersionId("connector-v1");

        var first = compiler.compile(route);
        var second = compiler.compile(route);

        assertThat(first.plan()).isEqualTo(second.plan());
        assertThat(first.hash()).isEqualTo(second.hash()).hasSize(64);
    }
}
