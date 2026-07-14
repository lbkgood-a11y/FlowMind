package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessClosurePlanCompilerTest {

    private final BusinessObjectCatalogService catalogService = mock(BusinessObjectCatalogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessClosurePlanCompiler compiler =
            new BusinessClosurePlanCompiler(catalogService, objectMapper);

    @Test
    void compilesExecutorKeysAndDisplayNamesIntoImmutablePlans() throws Exception {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog("更新报销单状态"));

        BusinessClosurePlanCompiler.CompiledBusinessClosurePlan plan =
                compiler.compile(definition());

        assertNotNull(plan.businessBindingSnapshot());
        JsonNode closurePlan = objectMapper.readTree(plan.closurePlanJson());
        assertEquals("ACTION", closurePlan.path("outcomes").path("APPROVED")
                .get(0).path("selectorType").asText());
        assertEquals("更新报销单状态", closurePlan.path("outcomes").path("APPROVED")
                .get(0).path("action").path("displayName").asText());
        assertEquals("expense_report.updateStatus", closurePlan.path("outcomes").path("APPROVED")
                .get(0).path("action").path("executorKey").asText());

        JsonNode agentPlan = objectMapper.readTree(plan.agentFollowUpPlanJson());
        assertEquals("expense_report.agent.paymentSummary",
                agentPlan.path("actions").get(0).path("agentAction").path("executorKey").asText());
    }

    @Test
    void publishedPlanKeepsCatalogSnapshotWhenCatalogDisplayNameChanges() throws Exception {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog("更新报销单状态"));
        BusinessClosurePlanCompiler.CompiledBusinessClosurePlan versionOne =
                compiler.compile(definition());

        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog("更新费用单状态"));
        BusinessClosurePlanCompiler.CompiledBusinessClosurePlan versionTwo =
                compiler.compile(definition());

        assertEquals("更新报销单状态", objectMapper.readTree(versionOne.closurePlanJson())
                .path("outcomes").path("APPROVED").get(0).path("action").path("displayName").asText());
        assertEquals("更新费用单状态", objectMapper.readTree(versionTwo.closurePlanJson())
                .path("outcomes").path("APPROVED").get(0).path("action").path("displayName").asText());
    }

    private ProcessPackageDefinition definition() {
        ProcessPackageDefinition definition = new ProcessPackageDefinition();
        ProcessPackageDefinition.BusinessObjectBinding binding =
                new ProcessPackageDefinition.BusinessObjectBinding();
        binding.setTypeCode("expense_report");
        definition.setBusinessObject(binding);

        ProcessPackageDefinition.ClosureEffectDefinition approved =
                new ProcessPackageDefinition.ClosureEffectDefinition();
        approved.setEffectKey("approved.updateStatus");
        approved.setActionCode("updateStatus");
        approved.setMode("HARD");
        approved.setParams(Map.of("status", "APPROVED"));
        ProcessPackageDefinition.ClosurePolicy closurePolicy =
                new ProcessPackageDefinition.ClosurePolicy();
        closurePolicy.setOutcomes(Map.of("APPROVED", List.of(approved)));
        definition.setClosurePolicy(closurePolicy);

        ProcessPackageDefinition.ClosureEffectDefinition agent =
                new ProcessPackageDefinition.ClosureEffectDefinition();
        agent.setAgentActionCode("paymentSummary");
        ProcessPackageDefinition.AgentFollowUpPolicy agentPolicy =
                new ProcessPackageDefinition.AgentFollowUpPolicy();
        agentPolicy.setActions(List.of(agent));
        definition.setAgentFollowUpPolicy(agentPolicy);
        return definition;
    }

    private BusinessObjectCatalogResponse catalog(String actionDisplayName) {
        BusinessObjectCatalogResponse response = new BusinessObjectCatalogResponse();
        response.setObject(new BusinessObjectSummaryResponse(
                "BIZ_EXPENSE_REPORT",
                "GLOBAL",
                "expense_report",
                "报销单",
                "expense-service",
                1,
                "PUBLISHED",
                null));

        BusinessObjectCatalogResponse.ActionItem action =
                new BusinessObjectCatalogResponse.ActionItem();
        action.setActionCode("updateStatus");
        action.setDisplayName(actionDisplayName);
        action.setActionType("UPDATE_STATUS");
        action.setExecutorKey("expense_report.updateStatus");
        response.setActions(List.of(action));

        BusinessObjectCatalogResponse.AgentActionItem agent =
                new BusinessObjectCatalogResponse.AgentActionItem();
        agent.setAgentActionCode("paymentSummary");
        agent.setDisplayName("付款准备摘要");
        agent.setExecutorKey("expense_report.agent.paymentSummary");
        response.setAgentActions(List.of(agent));
        return response;
    }
}
