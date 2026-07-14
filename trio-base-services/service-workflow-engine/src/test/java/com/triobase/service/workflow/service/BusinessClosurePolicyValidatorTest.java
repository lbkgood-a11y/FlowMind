package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.executor.ProcessExecutorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessClosurePolicyValidatorTest {

    private final BusinessObjectCatalogService catalogService = mock(BusinessObjectCatalogService.class);
    private final ProcessExecutorRegistry executorRegistry = mock(ProcessExecutorRegistry.class);
    private final BusinessClosurePolicyValidator validator =
            new BusinessClosurePolicyValidator(catalogService, executorRegistry, new ObjectMapper());

    @Test
    void acceptsRegisteredBusinessAndAgentActions() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        when(executorRegistry.hasBusinessOrClosureExecutor("expense_report.updateStatus"))
                .thenReturn(true);
        when(executorRegistry.hasAgentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(true);

        validator.validate(definition(
                effectWithAction("updateStatus"),
                effectWithAgent("paymentSummary", Map.of("amount", 1200, "businessId", "ER100"))));
    }

    @Test
    void rejectsBusinessActionWithoutRegisteredExecutor() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        when(executorRegistry.hasBusinessOrClosureExecutor("expense_report.updateStatus"))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(effectWithAction("updateStatus"), null)));

        assertEquals("BUSINESS_ACTION_EXECUTOR_NOT_REGISTERED", exception.getMessage());
    }

    @Test
    void rejectsStatusUpdateToUnknownBusinessStatus() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        when(executorRegistry.hasBusinessOrClosureExecutor("expense_report.updateStatus"))
                .thenReturn(true);
        ProcessPackageDefinition.ClosureEffectDefinition effect = effectWithAction("updateStatus");
        effect.setParams(Map.of("status", "PAID"));

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(effect, null)));

        assertEquals("BUSINESS_STATUS_NOT_IN_CATALOG", exception.getMessage());
    }

    @Test
    void rejectsBusinessRefFormFieldMissingFromFormSchema() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        ProcessPackageDefinition definition = definition(null, null);
        ProcessPackageDefinition.BusinessRefSource source =
                new ProcessPackageDefinition.BusinessRefSource();
        source.setSourceType("FORM_FIELD");
        source.setFieldKey("expenseReportId");
        definition.getBusinessObject().setBusinessRef(source);
        ProcessPackageDefinition.FormSchema form = new ProcessPackageDefinition.FormSchema();
        form.setSchema(Map.of("properties", Map.of("amount", Map.of("type", "number"))));
        definition.setForm(form);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition));

        assertEquals("BUSINESS_REF_FORM_FIELD_NOT_FOUND", exception.getMessage());
    }

    @Test
    void rejectsAgentActionWithoutRegisteredExecutor() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        when(executorRegistry.hasAgentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(null,
                        effectWithAgent("paymentSummary", Map.of("amount", 1200, "businessId", "ER100")))));

        assertEquals("AGENT_ACTION_EXECUTOR_NOT_REGISTERED", exception.getMessage());
    }

    @Test
    void rejectsAgentActionWithoutPermissionMapping() {
        BusinessObjectCatalogResponse catalog = catalog();
        catalog.getAgentActions().getFirst().setPermissionAction(null);
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog);
        when(executorRegistry.hasAgentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(null,
                        effectWithAgent("paymentSummary", Map.of("amount", 1200, "businessId", "ER100")))));

        assertEquals("AGENT_ACTION_PERMISSION_NOT_IN_CATALOG", exception.getMessage());
    }

    @Test
    void rejectsAgentActionMissingRequiredParameter() {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        when(executorRegistry.hasAgentFollowUpExecutor("expense_report.agent.paymentSummary"))
                .thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(null,
                        effectWithAgent("paymentSummary", Map.of("amount", 1200)))));

        assertEquals("AGENT_ACTION_PARAM_REQUIRED", exception.getMessage());
    }

    @Test
    void rejectsUrlSqlScriptDynamicClassAndFreePromptDefinitions() {
        assertForbidden(effect -> effect.setUrl("https://example.com/hook"));
        assertForbidden(effect -> effect.setSql("update expense set status='APPROVED'"));
        assertForbidden(effect -> effect.setScript("return true"));
        assertForbidden(effect -> effect.setClassName("com.example.DynamicExecutor"));
        assertForbidden(effect -> effect.setPrompt("call any tool you need"));
        assertForbidden(effect -> effect.setConnector(Map.of("name", "unsafe")));
        assertForbidden(effect -> effect.setToolCall(Map.of("tool", "unsafe")));
        assertForbidden(effect -> effect.setExecution(Map.of("url", "https://example.com/hook")));
    }

    private void assertForbidden(EffectMutator mutator) {
        when(catalogService.getPublishedDetail("expense_report")).thenReturn(catalog());
        ProcessPackageDefinition.ClosureEffectDefinition effect = effectWithAction("updateStatus");
        mutator.apply(effect);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validate(definition(effect, null)));

        assertEquals("BUSINESS_CLOSURE_FORBIDDEN_EXECUTION_DEFINITION", exception.getMessage());
    }

    private ProcessPackageDefinition definition(ProcessPackageDefinition.ClosureEffectDefinition businessEffect,
                                                ProcessPackageDefinition.ClosureEffectDefinition agentEffect) {
        ProcessPackageDefinition definition = new ProcessPackageDefinition();
        ProcessPackageDefinition.BusinessObjectBinding binding =
                new ProcessPackageDefinition.BusinessObjectBinding();
        binding.setTypeCode("expense_report");
        definition.setBusinessObject(binding);

        if (businessEffect != null) {
            ProcessPackageDefinition.ClosurePolicy closurePolicy =
                    new ProcessPackageDefinition.ClosurePolicy();
            closurePolicy.setOutcomes(Map.of("APPROVED", List.of(businessEffect)));
            definition.setClosurePolicy(closurePolicy);
        }
        if (agentEffect != null) {
            ProcessPackageDefinition.AgentFollowUpPolicy agentPolicy =
                    new ProcessPackageDefinition.AgentFollowUpPolicy();
            agentPolicy.setActions(List.of(agentEffect));
            definition.setAgentFollowUpPolicy(agentPolicy);
        }
        return definition;
    }

    private ProcessPackageDefinition.ClosureEffectDefinition effectWithAction(String actionCode) {
        ProcessPackageDefinition.ClosureEffectDefinition effect =
                new ProcessPackageDefinition.ClosureEffectDefinition();
        effect.setActionCode(actionCode);
        effect.setParams(Map.of("status", "APPROVED"));
        return effect;
    }

    private ProcessPackageDefinition.ClosureEffectDefinition effectWithAgent(String agentActionCode,
                                                                            Map<String, Object> params) {
        ProcessPackageDefinition.ClosureEffectDefinition effect =
                new ProcessPackageDefinition.ClosureEffectDefinition();
        effect.setAgentActionCode(agentActionCode);
        effect.setParams(params);
        return effect;
    }

    private BusinessObjectCatalogResponse catalog() {
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

        BusinessObjectCatalogResponse.StatusItem status =
                new BusinessObjectCatalogResponse.StatusItem();
        status.setStatusCode("APPROVED");
        response.setStatuses(List.of(status));

        BusinessObjectCatalogResponse.PermissionItem permission =
                new BusinessObjectCatalogResponse.PermissionItem();
        permission.setActionCode("submit");
        permission.setPermissionCode("/api/v1/process-instances/start:POST");
        BusinessObjectCatalogResponse.PermissionItem agentPermission =
                new BusinessObjectCatalogResponse.PermissionItem();
        agentPermission.setActionCode("agentFollowUp");
        agentPermission.setPermissionCode("/api/v1/process-agent-follow-up/*:POST");
        response.setPermissions(List.of(permission, agentPermission));

        BusinessObjectCatalogResponse.ActionItem action =
                new BusinessObjectCatalogResponse.ActionItem();
        action.setActionCode("updateStatus");
        action.setDisplayName("更新报销单状态");
        action.setActionType("UPDATE_STATUS");
        action.setExecutorKey("expense_report.updateStatus");
        response.setActions(List.of(action));

        BusinessObjectCatalogResponse.EventItem event =
                new BusinessObjectCatalogResponse.EventItem();
        event.setEventCode("ExpenseReportApproved");
        response.setEvents(List.of(event));

        BusinessObjectCatalogResponse.AgentActionItem agentAction =
                new BusinessObjectCatalogResponse.AgentActionItem();
        agentAction.setAgentActionCode("paymentSummary");
        agentAction.setDisplayName("付款准备摘要");
        agentAction.setExecutorKey("expense_report.agent.paymentSummary");
        agentAction.setPermissionAction("agentFollowUp");
        agentAction.setParamSchemaJson("""
                {"type":"object","required":["amount","businessId"]}
                """);
        response.setAgentActions(List.of(agentAction));
        return response;
    }

    private interface EffectMutator {
        void apply(ProcessPackageDefinition.ClosureEffectDefinition effect);
    }
}
