package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.service.openapi.service.MappingContractTestService;
import com.triobase.service.openapi.service.MappingPreviewService;
import com.triobase.service.openapi.service.MappingRegistryService;
import com.triobase.service.openapi.service.ValueMapService;
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
class MappingManagementControllerTest {

    @Mock private MappingRegistryService registryService;
    @Mock private MappingPreviewService previewService;
    @Mock private MappingContractTestService contractTestService;
    @Mock private ValueMapService valueMapService;

    @Test
    void everyMappingManagementOperationDeclaresPermission() {
        MappingManagementController controller = new MappingManagementController(
                registryService, previewService, contractTestService, valueMapService);
        assertThat(controller).isNotNull();
        Method[] methods = Arrays.stream(MappingManagementController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .toArray(Method[]::new);
        assertThat(methods).isNotEmpty();
        assertThat(methods).allMatch(method -> method.isAnnotationPresent(RequirePermission.class));
    }
}
