package com.triobase.service.openapi.action;

import com.triobase.common.action.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiActionDefinitionProviderTest {

    @Test
    void exposesOpenApiOwnerActionDefinitions() {
        var definitions = new OpenApiActionDefinitionProvider().definitions();

        assertThat(definitions)
                .extracting(ActionDefinition::getActionType)
                .containsExactlyInAnyOrder(
                        "integration.orchestration.start",
                        "integration.orchestration.cancel",
                        "integration.invocation.stateChanging",
                        "integration.callback.signal");
        assertThat(definitions)
                .allSatisfy(definition -> assertThat(definition.getOwnerService()).isEqualTo("service-openapi"));
    }
}
