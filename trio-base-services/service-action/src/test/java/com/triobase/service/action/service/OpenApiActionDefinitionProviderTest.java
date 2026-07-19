package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiActionDefinitionProviderTest {

    private final OpenApiActionDefinitionProvider provider = new OpenApiActionDefinitionProvider();

    @Test
    void registersOpenApiRuntimeActionDefinitions() {
        var definitions = provider.definitions();

        assertThat(definitions).extracting("actionType").containsExactly(
                "integration.orchestration.start",
                "integration.orchestration.cancel",
                "integration.invocation.stateChanging",
                "integration.callback.signal");
        assertThat(definitions).allMatch(definition ->
                "service-openapi".equals(definition.getOwnerService()));
        assertThat(definitions).anyMatch(definition ->
                "integration.orchestration.start".equals(definition.getActionType())
                        && definition.getExecutionMode() == ActionExecutionMode.WORKFLOW);
        assertThat(definitions).anyMatch(definition ->
                "integration.orchestration.cancel".equals(definition.getActionType())
                        && definition.getExecutionMode() == ActionExecutionMode.SIGNAL);
        assertThat(definitions).anyMatch(definition ->
                "integration.callback.signal".equals(definition.getActionType())
                        && definition.getExecutionMode() == ActionExecutionMode.SIGNAL);
    }
}
