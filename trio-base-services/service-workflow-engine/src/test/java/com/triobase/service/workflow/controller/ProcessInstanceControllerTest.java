package com.triobase.service.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.FormFieldValidationError;
import com.triobase.service.workflow.dto.ProcessVersionConflictResponse;
import com.triobase.service.workflow.exception.FormDataValidationException;
import com.triobase.service.workflow.exception.ProcessVersionConflictException;
import com.triobase.service.workflow.service.ProcessInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessInstanceControllerTest {

    private final ProcessInstanceService processInstanceService = mock(ProcessInstanceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProcessInstanceController(processInstanceService))
                .setControllerAdvice(new WorkflowExceptionHandler())
                .build();
    }

    @Test
    void returnsFieldErrorsInResponseData() throws Exception {
        when(processInstanceService.startProcess(any())).thenThrow(
                new FormDataValidationException(List.of(
                        new FormFieldValidationError("reason", "REQUIRED",
                                "required property is missing", "required"))));

        mockMvc.perform(post("/api/v1/process-instances/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "processKey", "expense_report",
                                "formData", java.util.Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("FORM_DATA_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void returnsCurrentPackageForVersionConflict() throws Exception {
        when(processInstanceService.startProcess(any())).thenThrow(
                new ProcessVersionConflictException(new ProcessVersionConflictResponse(
                        "PKG_V1", 1, "PKG_V2", 2)));

        mockMvc.perform(post("/api/v1/process-instances/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "processKey", "expense_report",
                                "processPackageId", "PKG_V1",
                                "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("PROCESS_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data.currentProcessPackageId").value("PKG_V2"))
                .andExpect(jsonPath("$.data.currentVersion").value(2));
    }
}
