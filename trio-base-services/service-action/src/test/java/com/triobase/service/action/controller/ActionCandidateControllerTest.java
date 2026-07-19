package com.triobase.service.action.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.service.action.dto.ActionCandidateBatchRequest;
import com.triobase.service.action.service.ActionCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActionCandidateControllerTest {

    @Mock
    private ActionCandidateService candidateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActionCandidateController(candidateService))
                .setControllerAdvice(new ActionExceptionHandler())
                .build();
    }

    @Test
    void validatesCandidate() throws Exception {
        ActionCandidateValidationResult result = new ActionCandidateValidationResult();
        result.setCandidateId("cand-1");
        result.setActionType("process.task.approve");
        result.setValid(true);
        result.setDispatchable(true);
        when(candidateService.validate(any(ActionCandidate.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/actions/candidates/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.dispatchable").value(true));
    }

    @Test
    void batchValidatesCandidates() throws Exception {
        ActionCandidateValidationResult result = new ActionCandidateValidationResult();
        result.setActionType("process.task.approve");
        result.setVisible(true);
        result.setEnabled(true);
        when(candidateService.validateBatch(any())).thenReturn(List.of(result));
        ActionCandidateBatchRequest request = new ActionCandidateBatchRequest();
        request.setCandidates(List.of(candidate()));

        mockMvc.perform(post("/api/v1/actions/candidates/batch-validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].enabled").value(true));
    }

    @Test
    void dispatchesCandidate() throws Exception {
        GlobalActionResult result = new GlobalActionResult();
        result.setActionId("act-1");
        result.setStatus(ActionStatus.SUCCEEDED);
        when(candidateService.dispatch(any(ActionCandidate.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/actions/candidates/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionId").value("act-1"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    private ActionCandidate candidate() {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setCandidateId("cand-1");
        candidate.setActionType("process.task.approve");
        candidate.getTarget().setId("task-1");
        candidate.getPayload().put("taskId", "task-1");
        return candidate;
    }
}
