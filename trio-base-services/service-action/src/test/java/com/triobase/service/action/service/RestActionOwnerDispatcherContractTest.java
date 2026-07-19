package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.action.config.ActionOwnerServiceProperties;
import com.triobase.service.action.entity.ActionExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestActionOwnerDispatcherContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private RestActionOwnerDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ActionOwnerServiceProperties properties = new ActionOwnerServiceProperties();
        properties.getBaseUrls().put("service-lowcode", "http://owner-service");
        InternalServiceSecurityProperties securityProperties = new InternalServiceSecurityProperties();
        securityProperties.setToken("internal-token");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        dispatcher = new RestActionOwnerDispatcher(properties, securityProperties, objectMapper, builder);
    }

    @Test
    void dispatchesSuccessfulOwnerExecution() {
        expectOwner("""
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "actionId": "act_1",
                    "status": "SUCCEEDED",
                    "ownerService": "service-lowcode",
                    "ownerExecutionRef": "form-1",
                    "data": {"formInstanceId": "form-1"}
                  }
                }
                """);

        GlobalActionResult result = dispatcher.dispatch(definition(), request(), execution());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(result.getOwnerExecutionRef()).isEqualTo("form-1");
        assertThat(result.getData()).containsEntry("formInstanceId", "form-1");
        server.verify();
    }

    @Test
    void mapsOwnerGuardFailureToRejectedResult() {
        expectOwner("""
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "actionId": "act_1",
                    "status": "REJECTED",
                    "ownerService": "service-lowcode",
                    "message": "SELF_APPROVAL_DENIED",
                    "errors": [
                      {
                        "code": "SELF_APPROVAL_DENIED",
                        "category": "GUARD",
                        "message": "submitter cannot approve"
                      }
                    ]
                  }
                }
                """);

        GlobalActionResult result = dispatcher.dispatch(definition(), request(), execution());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(result.getErrors()).extracting(error -> error.getCategory())
                .contains(ActionErrorCategory.GUARD);
        server.verify();
    }

    @Test
    void mapsOwnerExecutionFailureToFailedResult() {
        expectOwner("""
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "actionId": "act_1",
                    "status": "FAILED",
                    "ownerService": "service-lowcode",
                    "retryable": true,
                    "message": "OWNER_TEMPORARY_FAILURE",
                    "errors": [
                      {
                        "code": "OWNER_TEMPORARY_FAILURE",
                        "category": "EXECUTION",
                        "message": "temporary failure"
                      }
                    ]
                  }
                }
                """);

        GlobalActionResult result = dispatcher.dispatch(definition(), request(), execution());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getCode())
                .contains("OWNER_TEMPORARY_FAILURE");
        server.verify();
    }

    @Test
    void propagatesInternalHeadersTraceAndIdempotencyToOwner() {
        expectOwner("""
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "actionId": "act_1",
                    "status": "SUCCEEDED",
                    "ownerService": "service-lowcode"
                  }
                }
                """);

        dispatcher.dispatch(definition(), request(), execution());

        server.verify();
    }

    private void expectOwner(String responseJson) {
        server.expect(once(), requestTo("http://owner-service/internal/v1/actions/execute"))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-action"))
                .andExpect(header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "internal-token"))
                .andExpect(header(TraceUtil.TRACE_ID_KEY, "trace-1"))
                .andExpect(content().string(containsString("\"idempotencyKey\":\"idem-1\"")))
                .andExpect(content().string(containsString("\"actionId\":\"act_1\"")))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    }

    private ActionDefinition definition() {
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("lowcode.form.submit");
        definition.setOwnerService("service-lowcode");
        definition.setTargetType("LOWCODE_FORM");
        return definition;
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType("lowcode.form.submit");
        request.setSource(ActionSource.GUI);
        request.setIdempotencyKey("idem-1");
        request.setPayload(java.util.Map.of("amount", 100));

        ActionActor actor = new ActionActor();
        actor.setType(ActionActorType.USER);
        actor.setId("U1");
        actor.setTenantId("T1");
        request.setActor(actor);

        ActionTarget target = new ActionTarget();
        target.setType("LOWCODE_FORM");
        target.setId("expense");
        target.setOwnerService("service-lowcode");
        target.setTenantId("T1");
        request.setTarget(target);

        ActionContext context = new ActionContext();
        context.setTenantId("T1");
        context.setTraceId("trace-1");
        request.setContext(context);
        return request;
    }

    private ActionExecution execution() {
        ActionExecution execution = new ActionExecution();
        execution.setId("act_1");
        execution.setTenantId("T1");
        execution.setActionType("lowcode.form.submit");
        execution.setTraceId("trace-1");
        execution.setStatus("AUTHORIZED");
        return execution;
    }
}
