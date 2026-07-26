package com.triobase.service.apiruntime.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerActionStepRequestFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OwnerActionStepRequestFactory factory = new OwnerActionStepRequestFactory(objectMapper);

    @Test
    void buildsGlobalActionRequestFromOwnerActionStepAndPayloadPointers() throws Exception {
        var command = objectMapper.readTree("""
                {
                  "executionId":"exec-1",
                  "phase":"EXECUTE",
                  "context":{
                    "tenantId":"tenant-a",
                    "traceId":"trace-1",
                    "applicationClientId":"app-client-1",
                    "releaseId":"release-1",
                    "idempotencyKey":"idem-root"
                  },
                  "step":{
                    "key":"submitLeave",
                    "type":"OWNER_ACTION",
                    "ownerService":"service-lowcode",
                    "actionType":"lowcode.form.submit",
                    "targetType":"LOWCODE_FORM",
                    "targetId":"leave",
                    "payloadPointer":"/actionPayload",
                    "payload":{"appKey":"leave","actionCode":"submitAndLaunch"},
                    "idempotencyKeyPointer":"/requestId",
                    "executionMode":"SYNC"
                  },
                  "payload":{
                    "requestId":"external-001",
                    "actionPayload":{
                      "data":{
                        "applicant":"admin",
                        "leaveType":"ANNUAL",
                        "startDate":"2026-07-27",
                        "endDate":"2026-07-28",
                        "reason":"family"
                      }
                    }
                  }
                }
                """);

        var request = factory.from(command);

        assertThat(request.getActionId()).startsWith("act_");
        assertThat(request.getActionType()).isEqualTo("lowcode.form.submit");
        assertThat(request.getSource()).isEqualTo(ActionSource.API);
        assertThat(request.getExecutionMode()).isEqualTo(ActionExecutionMode.SYNC);
        assertThat(request.getIdempotencyKey())
                .isEqualTo("openapi:external-001:exec-1:submitLeave");
        assertThat(request.getActor().getType()).isEqualTo(ActionActorType.SERVICE);
        assertThat(request.getActor().getId()).isEqualTo("app-client-1");
        assertThat(request.getTarget().getOwnerService()).isEqualTo("service-lowcode");
        assertThat(request.getTarget().getType()).isEqualTo("LOWCODE_FORM");
        assertThat(request.getTarget().getId()).isEqualTo("leave");
        assertThat(request.getContext().getTenantId()).isEqualTo("tenant-a");
        assertThat(request.getContext().getTraceId()).isEqualTo("trace-1");
        assertThat(request.getContext().getCorrelationId()).isEqualTo("exec-1");
        assertThat(request.getPayload()).containsEntry("appKey", "leave");
        assertThat(request.getPayload()).containsEntry("actionCode", "submitAndLaunch");
        assertThat(request.getPayload().get("data")).isInstanceOf(Map.class);
    }
}
