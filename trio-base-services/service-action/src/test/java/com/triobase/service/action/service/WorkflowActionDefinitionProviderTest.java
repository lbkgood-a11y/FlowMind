package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowActionDefinitionProviderTest {

    private final WorkflowActionDefinitionProvider provider = new WorkflowActionDefinitionProvider();

    @Test
    void registersProcessTaskAndClosureActionDefinitions() {
        var definitions = provider.definitions();

        assertThat(definitions).extracting("actionType").containsExactly(
                "process.instance.start",
                "process.task.approve",
                "process.task.reject",
                "process.task.transfer",
                "process.task.addSign",
                "process.closure.effect.retry",
                "process.closure.effect.markHandled");
        assertThat(definitions).allMatch(definition ->
                "service-workflow-engine".equals(definition.getOwnerService()));
        assertThat(definitions).anyMatch(definition ->
                "process.instance.start".equals(definition.getActionType())
                        && definition.getExecutionMode() == ActionExecutionMode.WORKFLOW);
        assertThat(definitions).anyMatch(definition ->
                "process.task.approve".equals(definition.getActionType())
                        && definition.getExecutionMode() == ActionExecutionMode.SIGNAL);
    }
}
