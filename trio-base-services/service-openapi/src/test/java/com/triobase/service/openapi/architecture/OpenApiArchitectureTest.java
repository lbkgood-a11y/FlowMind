package com.triobase.service.openapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.archunit.ActionMutationEndpointRule;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.controller.OpenApiActionOwnerController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.triobase.service.openapi",
        importOptions = ImportOption.DoNotIncludeTests.class)
class OpenApiArchitectureTest {

    @org.junit.jupiter.api.Test
    void temporalWorkflowsRemainDeterministicAndFreeOfIo() {
        com.triobase.common.archunit.TemporalDeterministicRule.verify(
                "com.triobase.service.openapi.temporal");
    }

    @ArchTest
    static final ArchRule REST_CONTROLLERS_STAY_IN_CONTROLLER_PACKAGE = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule APPLICATION_ENTRY_POINT_STAYS_AT_ROOT = classes()
            .that().haveSimpleName("OpenApiApplication")
            .should().resideInAPackage("com.triobase.service.openapi");

    @ArchTest
    static final ArchRule PUBLIC_MUTATIONS_USE_ACTION_RUNTIME =
            ActionMutationEndpointRule.publicMutationEndpointsMustBeActionRouted(Set.of(
                    "ApplicationAccessManagementController",
                    "CallbackProfileManagementController",
                    "CallbackQuarantineController",
                    "CallbackRuntimeController",
                    "ConnectorManagementController",
                    "ExecutionOperationsController",
                    "IntegrationAdmissionController",
                    "MappingManagementController",
                    "OpenApiActionOwnerController",
                    "OrchestrationManagementController",
                    "RouteReleaseManagementController",
                    "StructureManagementController"
            ), Set.of(
                    "OrchestrationRuntimeController#start",
                    "OrchestrationRuntimeController#cancel"
            ));

    @Test
    void actionOwnerControllerExposesStandardInternalEndpoints() throws NoSuchMethodException {
        assertStandardActionOwnerEndpoints(OpenApiActionOwnerController.class);
    }

    private void assertStandardActionOwnerEndpoints(Class<?> controllerType) throws NoSuchMethodException {
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).contains("/internal/v1/actions");
        assertPostEndpoint(controllerType, "execute", "/execute");
        assertPostEndpoint(controllerType, "guard", "/guard");
    }

    private void assertPostEndpoint(Class<?> controllerType, String methodName, String path)
            throws NoSuchMethodException {
        Method method = controllerType.getDeclaredMethod(methodName, ActionOwnerDispatchRequest.class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertThat(postMapping).isNotNull();
        assertThat(postMapping.value()).contains(path);
        assertThat(method.getReturnType()).isEqualTo(R.class);
    }
}
