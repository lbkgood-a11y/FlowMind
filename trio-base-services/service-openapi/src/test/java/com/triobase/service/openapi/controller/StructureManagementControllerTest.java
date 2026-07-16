package com.triobase.service.openapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.StructureResponse;
import com.triobase.service.openapi.service.OpenApiExportService;
import com.triobase.service.openapi.service.OpenApiImportService;
import com.triobase.service.openapi.service.StructureRegistryService;
import com.triobase.service.openapi.service.StructureVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StructureManagementControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private StructureRegistryService registryService;
    @Mock private StructureVersionService versionService;
    @Mock private OpenApiImportService importService;
    @Mock private OpenApiExportService exportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StructureManagementController(
                registryService, versionService, importService, exportService)).build();
    }

    @Test
    void createsStructureThroughManagementApi() throws Exception {
        when(registryService.create(any())).thenReturn(new StructureResponse(
                "structure-1", "tenant-a", "purchase", "order", "Order", null,
                StructureKind.CANONICAL, StructureDirection.BIDIRECTIONAL,
                "DOMAIN", "procurement", AssetLifecycleState.ACTIVE,
                1, "version-1", VersionLifecycleState.DRAFT,
                LocalDateTime.now(), LocalDateTime.now()));
        String request = """
                {
                  "namespace":"purchase",
                  "structureKey":"order",
                  "displayName":"Order",
                  "structureKind":"CANONICAL",
                  "direction":"BIDIRECTIONAL",
                  "ownerType":"DOMAIN",
                  "ownerId":"procurement",
                  "schemaContent":{"type":"object","properties":{}}
                }
                """;

        mockMvc.perform(post("/api/v1/openapi/management/structures")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("structure-1"));
    }

    @Test
    void everyManagementOperationDeclaresPermission() {
        Method[] mappedMethods = Arrays.stream(StructureManagementController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .toArray(Method[]::new);

        assertThat(mappedMethods).isNotEmpty();
        assertThat(mappedMethods).allMatch(method -> method.isAnnotationPresent(RequirePermission.class));
    }
}
