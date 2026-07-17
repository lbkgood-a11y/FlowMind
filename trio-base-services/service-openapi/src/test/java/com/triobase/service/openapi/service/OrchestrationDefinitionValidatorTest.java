package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationDefinitionValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrchestrationDefinitionValidator validator =
            new OrchestrationDefinitionValidator(objectMapper);

    @Test
    void acceptsVersionedBranchParallelTimerAndCompensationSchema() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {
                  "schemaVersion":"1",
                  "start":"branch",
                  "steps":[
                    {"key":"branch","type":"BRANCH","branches":[
                      {"pointer":"/priority","equals":"HIGH","next":"parallel"}
                    ],"defaultNext":"timer"},
                    {"key":"parallel","type":"PARALLEL","children":["map","invoke"],"next":"timer"},
                    {"key":"map","type":"TRANSFORM","mappingVersionId":"map-v1"},
                    {"key":"invoke","type":"INVOKE","connectorVersionId":"connector-v1",
                     "compensationStep":"undo","retryPreset":"IDEMPOTENT"},
                    {"key":"timer","type":"WAIT","durationSeconds":1,"next":"end"},
                    {"key":"undo","type":"COMPENSATE","connectorVersionId":"connector-undo"},
                    {"key":"end","type":"END"}
                  ]
                }
                """);

        var result = validator.validate("1", definition);

        assertThat(result.valid()).as(result.errors().toString()).isTrue();
    }

    @Test
    void allowsOnlyBoundedWaitBackEdges() throws Exception {
        JsonNode bounded = objectMapper.readTree("""
                {"schemaVersion":"1","start":"invoke","steps":[
                  {"key":"invoke","type":"INVOKE","connectorVersionId":"c1","next":"wait"},
                  {"key":"wait","type":"WAIT","durationSeconds":1,"loopTo":"invoke",
                   "maxIterations":3,"next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);
        JsonNode unbounded = objectMapper.readTree("""
                {"schemaVersion":"1","start":"a","steps":[
                  {"key":"a","type":"TRANSFORM","mappingVersionId":"m1","next":"b"},
                  {"key":"b","type":"TRANSFORM","mappingVersionId":"m2","next":"a"}
                ]}
                """);

        assertThat(validator.validate("1", bounded).valid()).isTrue();
        assertThat(validator.validate("1", unbounded).errors())
                .anyMatch(error -> error.startsWith("UNSUPPORTED_CYCLE"));
    }

    @Test
    void rejectsUnknownReferencesUnreachableStepsAndExecutableConfiguration() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"invoke","steps":[
                  {"key":"invoke","type":"INVOKE","connectorVersionId":"c1",
                   "next":"missing","script":"system.exit()"},
                  {"key":"orphan","type":"END"}
                ]}
                """);

        var result = validator.validate("1", definition);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("MISSING_STEP_REFERENCE"));
        assertThat(result.errors()).anyMatch(error -> error.contains("UNREACHABLE_STEP"));
        assertThat(result.errors()).anyMatch(error -> error.contains("FORBIDDEN_CONFIGURATION"));
    }

    @Test
    void supportsSignalWaitWithDeterministicTimeout() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {"schemaVersion":"1","start":"callback","steps":[
                  {"key":"callback","type":"WAIT","signalName":"partner-result",
                   "timeoutSeconds":300,"next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);

        assertThat(validator.validate("1", definition).valid()).isTrue();
    }
}
