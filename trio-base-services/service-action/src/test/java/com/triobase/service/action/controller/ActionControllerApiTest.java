package com.triobase.service.action.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionEventType;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.action.dto.ActionEventResponse;
import com.triobase.service.action.dto.ActionExecutionResponse;
import com.triobase.service.action.dto.ActionQueryCriteria;
import com.triobase.service.action.exception.ActionRuntimeException;
import com.triobase.service.action.service.ActionQueryService;
import com.triobase.service.action.service.ActionRuntimePipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActionControllerApiTest {

    @Mock
    private ActionRuntimePipeline runtimePipeline;
    @Mock
    private ActionQueryService queryService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActionController(runtimePipeline, queryService))
                .setControllerAdvice(new ActionExceptionHandler())
                .build();
    }

    @Test
    void submitActionReturnsNormalizedStatus() throws Exception {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId("act_1");
        result.setActionType("lowcode.form.submit");
        result.setStatus(ActionStatus.SUCCEEDED);
        when(runtimePipeline.submit(any(GlobalActionRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionId").value("act_1"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void actionRuntimeExceptionReturnsActionErrorResponse() throws Exception {
        when(runtimePipeline.submit(any(GlobalActionRequest.class)))
                .thenThrow(new ActionRuntimeException(40341,
                        ActionErrorCategory.AUTHORIZATION,
                        "ACTION_AUTHORIZATION_DENIED"));

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40341))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.errors[0].category").value("AUTHORIZATION"));
    }

    @Test
    void getActionDetailReturnsExecution() throws Exception {
        when(queryService.detail("act_1")).thenReturn(executionResponse());

        mockMvc.perform(get("/api/v1/actions/act_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionId").value("act_1"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void getActionEventsReturnsOrderedEvents() throws Exception {
        when(queryService.events("act_1")).thenReturn(List.of(eventResponse()));

        mockMvc.perform(get("/api/v1/actions/act_1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventId").value("evt_1"))
                .andExpect(jsonPath("$.data[0].eventType").value("CREATED"));
    }

    @Test
    void streamActionEventsUsesSseFormatting() throws Exception {
        when(queryService.events("act_1")).thenReturn(List.of(eventResponse()));

        MvcResult result = mockMvc.perform(get("/api/v1/actions/act_1/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:CREATED")));
    }

    @Test
    void queryActionsPassesAuditFiltersToQueryService() throws Exception {
        when(queryService.query(any(ActionQueryCriteria.class)))
                .thenReturn(PageResult.of(List.of(executionResponse()), 1, 2, 10));

        mockMvc.perform(get("/api/v1/actions")
                        .param("page", "2")
                        .param("size", "10")
                        .param("actorId", "U1")
                        .param("source", "GUI")
                        .param("targetType", "LOWCODE_FORM")
                        .param("status", "SUCCEEDED")
                        .param("traceId", "trace-1")
                        .param("correlationId", "corr-1")
                        .param("idempotencyKey", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actionId").value("act_1"));

        ArgumentCaptor<ActionQueryCriteria> captor = ArgumentCaptor.forClass(ActionQueryCriteria.class);
        verify(queryService).query(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getActorId()).isEqualTo("U1");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
    }

    private GlobalActionRequest actionRequest() {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionType("lowcode.form.submit");
        request.setPayload(Map.of("amount", 100));
        request.setIdempotencyKey("idem-1");
        return request;
    }

    private ActionExecutionResponse executionResponse() {
        ActionExecutionResponse response = new ActionExecutionResponse();
        response.setActionId("act_1");
        response.setTenantId("T1");
        response.setActionType("lowcode.form.submit");
        response.setStatus("SUCCEEDED");
        response.setSource("GUI");
        response.setTraceId("trace-1");
        response.setCorrelationId("corr-1");
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }

    private ActionEventResponse eventResponse() {
        ActionEventResponse response = new ActionEventResponse();
        response.setEventId("evt_1");
        response.setActionId("act_1");
        response.setTenantId("T1");
        response.setEventType(ActionEventType.CREATED.name());
        response.setStatus(ActionStatus.CREATED.name());
        response.setSequenceNo(1);
        response.setMessage("ACTION_CREATED");
        response.setOccurredAt(LocalDateTime.now());
        return response;
    }
}
