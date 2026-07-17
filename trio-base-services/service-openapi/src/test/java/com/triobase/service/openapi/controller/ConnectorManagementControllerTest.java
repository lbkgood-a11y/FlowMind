package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.service.openapi.service.ConnectorRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConnectorManagementControllerTest {

    @Mock private ConnectorRegistryService registryService;

    @Test
    void everyConnectorManagementOperationDeclaresPermission() {
        assertThat(new ConnectorManagementController(registryService)).isNotNull();
        Method[] methods = Arrays.stream(ConnectorManagementController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .toArray(Method[]::new);
        assertThat(methods).isNotEmpty();
        assertThat(methods).allMatch(method -> method.isAnnotationPresent(RequirePermission.class));
    }
}
