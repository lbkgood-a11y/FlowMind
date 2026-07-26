package com.triobase.service.lowcode.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateBatchRequest;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.archunit.ActionMutationEndpointRule;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.action.LowcodeOwnerHostedActionController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.triobase.service.lowcode",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LowcodeActionArchitectureTest {

    @ArchTest
    static final ArchRule PUBLIC_MUTATIONS_USE_ACTION_RUNTIME =
            ActionMutationEndpointRule.publicMutationEndpointsMustBeActionRouted(Set.of(
                    "ApplicationController",
                    "FormDefinitionController"));

    @Test
    void ownerHostedActionControllerExposesStandardEndpoints() throws NoSuchMethodException {
        assertStandardOwnerHostedEndpoints(LowcodeOwnerHostedActionController.class,
                "/api/v1/lowcode-runtime/actions");
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
