package com.triobase.service.lowcode.action;

import com.triobase.common.action.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LowcodeActionDefinitionProviderTest {

    @Test
    void exposesLowcodeOwnerActionDefinitions() {
        var definitions = new LowcodeActionDefinitionProvider().definitions();

        assertThat(definitions)
                .extracting(ActionDefinition::getActionType)
                .containsExactlyInAnyOrder(
                        "lowcode.form.create",
                        "lowcode.form.save",
                        "lowcode.form.submit",
                        "lowcode.workflow.retry");
        assertThat(definitions)
                .allSatisfy(definition -> assertThat(definition.getOwnerService()).isEqualTo("service-lowcode"));
    }
}
