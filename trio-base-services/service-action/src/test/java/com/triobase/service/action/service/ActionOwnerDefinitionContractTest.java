package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ActionOwnerDefinitionContractTest {

    @Test
    void allBusinessActionOwnersRegisterDefinitionsWithFrontendMetadata() {
        List<ActionDefinition> definitions = definitions();

        assertThat(definitions).isNotEmpty();
        assertThat(definitions).allSatisfy(definition -> {
            assertThat(definition.getActionType()).isNotBlank();
            assertThat(definition.getOwnerService()).isIn(
                    "service-workflow-engine",
                    "service-lowcode",
                    "service-openapi");
            assertThat(definition.getTargetType()).isNotBlank();
            assertThat(definition.getExecutionMode()).isNotNull();
            assertThat(definition.getDefaultRefreshScopes()).isNotEmpty();
            assertThat(definition.getTargetStatus()).isNotBlank();
            assertThat(definition.getTargetStatusGroup()).isNotBlank();
            assertThat(definition.getRequiredGuards()).allSatisfy(guard ->
                    assertThat(guard.getOwnerService()).isEqualTo(definition.getOwnerService()));
        });
    }

    @Test
    void actionTypesAreUniqueAcrossOwnerDefinitions() {
        List<ActionDefinition> definitions = definitions();

        assertThat(definitions)
                .extracting(ActionDefinition::getActionType)
                .doesNotHaveDuplicates();
    }

    @Test
    void definitionProvidersCoverCurrentBusinessActionOwnerServices() {
        Set<String> ownerServices = definitions().stream()
                .map(ActionDefinition::getOwnerService)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(ownerServices).containsExactlyInAnyOrder(
                "service-workflow-engine",
                "service-lowcode",
                "service-openapi");
    }

    private List<ActionDefinition> definitions() {
        return Stream.of(
                        new WorkflowActionDefinitionProvider().definitions(),
                        new LowcodeActionDefinitionProvider().definitions(),
                        new OpenApiActionDefinitionProvider().definitions())
                .flatMap(List::stream)
                .toList();
    }
}
