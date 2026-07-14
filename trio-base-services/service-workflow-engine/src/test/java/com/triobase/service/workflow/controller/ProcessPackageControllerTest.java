package com.triobase.service.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.ProcessPackageResponse;
import com.triobase.service.workflow.service.ProcessPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessPackageControllerTest {

    private final ProcessPackageService processPackageService = mock(ProcessPackageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProcessPackageController(processPackageService)).build();
    }

    @Test
    void updateDraftUsesVersionedPackageEndpoint() throws Exception {
        ProcessPackageResponse response = response("PKG001", 1, "DRAFT");
        when(processPackageService.update(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/process-packages/PKG001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("PKG001"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(processPackageService).update(any(), any());
    }

    @Test
    void createNewVersionReturnsDerivedDraft() throws Exception {
        ProcessPackageResponse response = response("PKG002", 2, "DRAFT");
        when(processPackageService.createNewVersion("PKG001")).thenReturn(response);

        mockMvc.perform(post("/api/v1/process-packages/PKG001/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("PKG002"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(processPackageService).createNewVersion("PKG001");
    }

    private ProcessPackageResponse response(String id, int version, String status) {
        ProcessPackageResponse response = new ProcessPackageResponse();
        response.setId(id);
        response.setProcessKey("expense_test");
        response.setName("Expense Test");
        response.setVersion(version);
        response.setStatus(status);
        return response;
    }
}
