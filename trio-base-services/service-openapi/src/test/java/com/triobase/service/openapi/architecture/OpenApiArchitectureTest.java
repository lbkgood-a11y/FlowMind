package com.triobase.service.openapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateBatchRequest;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.archunit.ActionMutationEndpointRule;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.controller.OpenApiOwnerHostedActionController;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.triobase.service.openapi",
        importOptions = ImportOption.DoNotIncludeTests.class)
class OpenApiArchitectureTest {

    @ArchTest
    static final ArchRule OPENAPI_CONTROL_PLANE_DOES_NOT_HOST_TEMPORAL_RUNTIME =
            noClasses().should().resideInAPackage("..temporal..");

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
                    "ConnectorManagementController",
                    "ExecutionOperationsController",
                    "IntegrationAdmissionController",
                    "MappingManagementController",
                    "OpenApiOwnerHostedActionController",
                    "OrchestrationManagementController",
                    "RouteReleaseManagementController",
                    "StructureManagementController"
            ), Set.of());

    @Test
    void ownerHostedActionControllerExposesStandardEndpoints() throws NoSuchMethodException {
        assertStandardOwnerHostedEndpoints(OpenApiOwnerHostedActionController.class,
                "/api/v1/openapi/management/actions");
    }

    @Test
    void sharedRuntimePrimitivesStayInOpenApiCommon() {
        Set<String> forbiddenLocalCopies = Set.of(
                "JsonPayloadValidator",
                "JsonTreeAccess",
                "MappingSecurityValidator",
                "MappingOperation",
                "MappingRuleRequest",
                "TransformationResult",
                "SensitiveDataRedactor",
                "GatewayTrustVerifier",
                "CredentialMaterial",
                "CredentialProvider",
                "OAuth2TokenProvider",
                "OutboundAuthentication",
                "OutboundAuthenticationResolver",
                "DefaultReleaseResolver",
                "DefaultSubscriptionAccessChecker"
        );
        assertThat(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.triobase.service.openapi")
                .stream()
                .map(JavaClass::getSimpleName))
                .doesNotContainAnyElementsOf(forbiddenLocalCopies);
    }

    @Test
    void credentialPrimitivesStayInOpenApiCommon() {
        assertThat(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.triobase.service.openapi")
                .stream()
                .map(JavaClass::getSimpleName))
                .doesNotContain(
                        "CredentialMaterial",
                        "CredentialProvider",
                        "OAuth2TokenProvider",
                        "OutboundAuthentication",
                        "OutboundAuthenticationResolver");
    }

    private void assertStandardOwnerHostedEndpoints(Class<?> controllerType, String path) throws NoSuchMethodException {
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).contains(path);
        assertGetEndpoint(controllerType, "definitions", "/definitions");
        assertPostEndpoint(controllerType, "validate", "/candidates/validate", ActionCandidate.class);
        assertPostEndpoint(controllerType, "validateBatch", "/candidates/batch-validate",
                ActionCandidateBatchRequest.class);
        assertPostEndpoint(controllerType, "dispatchCandidate", "/candidates/dispatch", ActionCandidate.class);
        assertPostEndpoint(controllerType, "dispatch", "/dispatch", GlobalActionRequest.class);
    }

    private void assertGetEndpoint(Class<?> controllerType, String methodName, String path)
            throws NoSuchMethodException {
        Method method = controllerType.getDeclaredMethod(methodName);
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).contains(path);
        assertThat(method.getReturnType()).isEqualTo(R.class);
    }

    private void assertPostEndpoint(Class<?> controllerType, String methodName, String path, Class<?> requestType)
            throws NoSuchMethodException {
        Method method = controllerType.getDeclaredMethod(methodName, requestType);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertThat(postMapping).isNotNull();
        assertThat(postMapping.value()).contains(path);
        assertThat(method.getReturnType()).isEqualTo(R.class);
    }
}
