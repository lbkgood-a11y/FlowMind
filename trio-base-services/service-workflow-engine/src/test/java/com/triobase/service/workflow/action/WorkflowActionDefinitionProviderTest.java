package com.triobase.service.workflow.action;

import com.triobase.common.action.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowActionDefinitionProviderTest {

    @Test
    void exposesWorkflowOwnerActionDefinitions() {
        var definitions = new WorkflowActionDefinitionProvider().definitions();

        assertThat(definitions)
                .extracting(ActionDefinition::getActionType)
                .containsExactlyInAnyOrder(
                        "process.instance.start",
                        "process.task.approve",
                        "process.task.reject",
                        "process.task.transfer",
                        "process.task.addSign",
                        "process.closure.effect.retry",
                        "process.closure.effect.markHandled");
        assertThat(definitions).allSatisfy(definition ->
                assertThat(definition.getOwnerService()).isEqualTo("service-workflow-engine"));
    }
}
