package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LowcodeControllerPermissionTest {

    @Test
    void publicFormDefinitionEndpointsDeclarePermissions() {
        assertMappedMethodsDeclarePermissions(FormDefinitionController.class);
    }

    @Test
    void publicFormInstanceEndpointsDeclarePermissions() {
        assertMappedMethodsDeclarePermissions(FormInstanceController.class);
    }

    @Test
    void publicApplicationEndpointsDeclarePermissions() {
        assertMappedMethodsDeclarePermissions(ApplicationController.class);
    }

    @Test
    void publicApplicationRuntimeEndpointsDeclarePermissions() {
        assertMappedMethodsDeclarePermissions(ApplicationRuntimeController.class);
    }

    private void assertMappedMethodsDeclarePermissions(Class<?> controllerType) {
        List<Method> mappedMethods = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(this::isMappedEndpoint)
                .toList();

        assertThat(mappedMethods).isNotEmpty();
        assertThat(mappedMethods)
                .extracting(Method::getName)
                .doesNotContain("lambda");
        assertThat(mappedMethods).allMatch(method -> method.isAnnotationPresent(RequirePermission.class));
    }

    private boolean isMappedEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
