package com.triobase.service.openapi.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedApplicationPilotContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pilotPinsGovernedAssetsForReadAndCallbackBasedStateChange() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/pilot/managed-application-pilot.json")) {
            assertThat(input).isNotNull();
            JsonNode pilot = objectMapper.readTree(input);

            assertThat(pilot.path("publicRuntimeEnabled").asBoolean()).isFalse();
            assertThat(pilot.at("/application/state").asText()).isEqualTo("ACTIVE");
            assertThat(pilot.at("/application/credential/secretReference").asText())
                    .startsWith("vault:");
            assertThat(pilot.at("/product/version").asText()).isEqualTo("1.0.0");
            assertThat(pilot.path("canonicalContracts")).allMatch(
                    contract -> "PUBLISHED".equals(contract.path("state").asText()));

            JsonNode routes = pilot.path("routes");
            assertThat(StreamSupport.stream(routes.spliterator(), false)
                    .anyMatch(route -> "SYNCHRONOUS".equals(route.path("mode").asText())
                            && "READ_ONLY".equals(route.path("operationClass").asText())
                            && route.path("timeoutMillis").asInt() < 500)).isTrue();
            assertThat(StreamSupport.stream(routes.spliterator(), false)
                    .anyMatch(route -> "ORCHESTRATED".equals(route.path("mode").asText())
                            && "STATE_CHANGING".equals(route.path("operationClass").asText())
                            && route.hasNonNull("callbackKey")
                            && route.path("steps").toString().contains("wait-callback"))).isTrue();
            assertThat(StreamSupport.stream(routes.spliterator(), false)
                    .allMatch(route -> route.path("storedContractTests").size() >= 2)).isTrue();

            String serialized = pilot.toString().toLowerCase();
            assertThat(serialized).doesNotContain("plaintextsecret", "authorization\":", "privatekey\":");
        }
    }
}
