package com.triobase.service.lowcode.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.triobase.common.action.owner.ActionOwnerDispatchRequest;
import com.triobase.common.archunit.ActionMutationEndpointRule;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.action.LowcodeActionOwnerController;
import org.junit.jupiter.api.Test;
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
    void actionOwnerControllerExposesStandardInternalEndpoints() throws NoSuchMethodException {
        assertStandardActionOwnerEndpoints(LowcodeActionOwnerController.class);
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
