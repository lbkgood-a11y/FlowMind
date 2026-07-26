package com.triobase.service.apiruntime.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.ActionTarget;
import com.triobase.common.action.model.GlobalActionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OwnerHostedActionDispatchClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final OwnerActionDispatchProperties properties = new OwnerActionDispatchProperties();
    private final OwnerHostedActionDispatchClient client =
            new OwnerHostedActionDispatchClient(restTemplate, properties, objectMapper);

    @Test
    void dispatchesToConfiguredOwnerEndpointAndParsesGlobalActionResult() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://localhost:8085/api/v1/lowcode-runtime/actions/dispatch"))
                .andExpect(method(POST))
                .andExpect(header("X-B3-TraceId", "trace-1"))
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{
                          "actionId":"act-1",
                          "actionType":"lowcode.form.submit",
                          "status":"SUCCEEDED",
                          "ownerService":"service-lowcode",
                          "ownerExecutionRef":"pi-1",
                          "data":{"runtimeStatus":"WORKFLOW_STARTED"}
                        }}
                        """, MediaType.APPLICATION_JSON));

        var result = client.dispatch(request());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(result.getOwnerService()).isEqualTo("service-lowcode");
        assertThat(result.getOwnerExecutionRef()).isEqualTo("pi-1");
        server.verify();
    }

    @Test
    void rejectsUnconfiguredOwnerServiceBeforeNetworkCall() {
        GlobalActionRequest request = request();
        request.getTarget().setOwnerService("service-unknown");

        assertThatThrownBy(() -> client.dispatch(request))
                .isInstanceOf(OwnerActionDispatchException.class)
                .hasMessage("OPENAPI_OWNER_ACTION_OWNER_NOT_CONFIGURED");
    }

    private GlobalActionRequest request() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId("act-1");
        request.setActionType("lowcode.form.submit");
        ActionTarget target = new ActionTarget();
        target.setOwnerService("service-lowcode");
        target.setType("LOWCODE_FORM");
        target.setId("leave");
        request.setTarget(target);
        ActionContext context = new ActionContext();
        context.setTraceId("trace-1");
        request.setContext(context);
        return request;
    }
}
