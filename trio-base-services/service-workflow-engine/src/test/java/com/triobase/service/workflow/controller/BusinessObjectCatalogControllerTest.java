package com.triobase.service.workflow.controller;

import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.service.BusinessObjectCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessObjectCatalogControllerTest {

    private final BusinessObjectCatalogService catalogService = mock(BusinessObjectCatalogService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BusinessObjectCatalogController(catalogService)).build();
    }

    @Test
    void listReturnsPublishedBusinessObjects() throws Exception {
        when(catalogService.listPublishedForCurrentTenant()).thenReturn(List.of(
                new BusinessObjectSummaryResponse("BIZ_EXPENSE_REPORT", "GLOBAL", "expense_report",
                        "报销单", "expense-service", 1, "PUBLISHED", "费用报销")));

        mockMvc.perform(get("/api/v1/process-business-objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].typeCode").value("expense_report"))
                .andExpect(jsonPath("$.data[0].displayName").value("报销单"));

        verify(catalogService).listPublishedForCurrentTenant();
    }

    @Test
    void detailHidesExecutorInternalsFromDesignerResponse() throws Exception {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());

        mockMvc.perform(get("/api/v1/process-business-objects/expense_report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.object.typeCode").value("expense_report"))
                .andExpect(jsonPath("$.data.statuses[0].displayName").value("已通过"))
                .andExpect(jsonPath("$.data.actions[0].actionCode").value("updateStatus"))
                .andExpect(jsonPath("$.data.actions[0].executorKey").doesNotExist())
                .andExpect(jsonPath("$.data.agentActions[0].agentActionCode").value("paymentSummary"))
                .andExpect(jsonPath("$.data.agentActions[0].executorKey").doesNotExist());

        verify(catalogService).getPublishedDetail("expense_report");
    }

    @Test
    void childEndpointsReturnSelectableCatalogGroups() throws Exception {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());

        mockMvc.perform(get("/api/v1/process-business-objects/expense_report/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("更新报销单状态"))
                .andExpect(jsonPath("$.data[0].executorKey").doesNotExist());

        mockMvc.perform(get("/api/v1/process-business-objects/expense_report/agent-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("付款准备摘要"))
                .andExpect(jsonPath("$.data[0].executorKey").doesNotExist());
    }

    private BusinessObjectCatalogResponse catalog() {
        BusinessObjectCatalogResponse response = new BusinessObjectCatalogResponse();
        response.setObject(new BusinessObjectSummaryResponse("BIZ_EXPENSE_REPORT", "GLOBAL",
                "expense_report", "报销单", "expense-service", 1, "PUBLISHED", "费用报销"));

        BusinessObjectCatalogResponse.StatusItem status = new BusinessObjectCatalogResponse.StatusItem();
        status.setStatusCode("APPROVED");
        status.setDisplayName("已通过");
        status.setTerminal(true);
        response.setStatuses(List.of(status));

        BusinessObjectCatalogResponse.ActionItem action = new BusinessObjectCatalogResponse.ActionItem();
        action.setActionCode("updateStatus");
        action.setDisplayName("更新报销单状态");
        action.setActionType("UPDATE_STATUS");
        action.setModeDefault("HARD");
        response.setActions(List.of(action));

        BusinessObjectCatalogResponse.AgentActionItem agent = new BusinessObjectCatalogResponse.AgentActionItem();
        agent.setAgentActionCode("paymentSummary");
        agent.setDisplayName("付款准备摘要");
        agent.setModeDefault("ASYNC");
        response.setAgentActions(List.of(agent));
        return response;
    }
}
