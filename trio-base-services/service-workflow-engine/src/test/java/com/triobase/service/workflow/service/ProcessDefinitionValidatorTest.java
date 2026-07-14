package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProcessDefinitionValidatorTest {

    private ProcessDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        validator = new ProcessDefinitionValidator(
                objectMapper, new RestrictedConditionEvaluator(objectMapper),
                mock(BusinessClosurePolicyValidator.class));
    }

    @Test
    void acceptsConnectedFeeReportFlow() {
        validator.validate("""
                {
                  "flow": {"nodes": [
                    {"id":"start","type":"START","name":"Start",
                     "next":[{"condition":"true","target":"dept"}]},
                    {"id":"dept","type":"APPROVAL","name":"Department",
                     "assignment":{"type":"ROLE","roleCode":"DEPT_HEAD"},
                     "next":[
                       {"condition":"amount > 5000","target":"finance"},
                       {"condition":"true","target":"end"}
                     ]},
                    {"id":"finance","type":"APPROVAL","name":"Finance",
                     "assignment":{"type":"ROLE","roleCode":"FINANCE"},
                     "next":[{"condition":"true","target":"end"}]},
                    {"id":"end","type":"END","name":"End"}
                  ]}
                }
                """);
    }

    @Test
    void rejectsMissingDefaultCondition() {
        assertValidationError("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START","next":[{"condition":"amount > 1","target":"end"}]},
                  {"id":"end","type":"END"}
                ]}}
                """, "PROCESS_REQUIRES_ONE_DEFAULT_CONDITION");
    }

    @Test
    void rejectsUnsupportedNodeAndMissingParticipant() {
        assertValidationError("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"script","type":"SERVICE_TASK"},
                  {"id":"end","type":"END"}
                ]}}
                """, "UNSUPPORTED_PROCESS_NODE_TYPE");

        assertValidationError("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"approval","type":"APPROVAL"},
                  {"id":"end","type":"END"}
                ]}}
                """, "PROCESS_PARTICIPANT_REQUIRED");
    }

    @Test
    void rejectsUnreachableNodeAndInvalidCountersignStrategy() {
        assertValidationError("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START","next":[{"condition":"true","target":"end"}]},
                  {"id":"orphan","type":"APPROVAL","assignment":{"type":"USER","userId":"u1"}},
                  {"id":"end","type":"END"}
                ]}}
                """, "PROCESS_CONTAINS_UNREACHABLE_NODES");

        assertValidationError("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"joint","type":"COUNTERSIGN","strategy":"MAJORITY",
                   "assignment":{"type":"ROLE","roleCode":"FINANCE"}},
                  {"id":"end","type":"END"}
                ]}}
                """, "INVALID_COUNTERSIGN_STRATEGY");
    }

    private void assertValidationError(String json, String errorCode) {
        BizException exception = assertThrows(BizException.class, () -> validator.validate(json));
        assertEquals(errorCode, exception.getMessage());
    }
}
