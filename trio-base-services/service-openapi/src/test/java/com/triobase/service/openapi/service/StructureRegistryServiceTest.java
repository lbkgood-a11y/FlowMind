package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.dto.CreateStructureRequest;
import com.triobase.service.openapi.infrastructure.mapper.OpenApiStructureMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureFieldMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructureRegistryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OpenApiStructureMapper structureMapper;
    @Mock
    private StructureVersionMapper versionMapper;
    @Mock
    private TenantExtensionValidator tenantExtensionValidator;
    @Mock
    private StructureSchemaInspector schemaInspector;
    @Mock
    private StructureFieldMapper fieldMapper;
    @Mock
    private IntegrationAuditService auditService;

    private StructureRegistryService service;

    @BeforeEach
    void setUp() {
        service = new StructureRegistryService(
                structureMapper, versionMapper, tenantExtensionValidator, schemaInspector, fieldMapper, auditService);
        lenient().when(schemaInspector.inspect(any())).thenReturn(List.of());
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of("TENANT_ADMIN"), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsStableIdentityAndInitialDraft() throws Exception {
        when(structureMapper.selectCount(any())).thenReturn(0L);
        CreateStructureRequest request = validRequest();

        var response = service.create(request);

        ArgumentCaptor<OpenApiStructure> structureCaptor = ArgumentCaptor.forClass(OpenApiStructure.class);
        ArgumentCaptor<StructureVersion> versionCaptor = ArgumentCaptor.forClass(StructureVersion.class);
        verify(structureMapper).insert(structureCaptor.capture());
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(structureCaptor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(versionCaptor.getValue().getStructureId()).isEqualTo(structureCaptor.getValue().getId());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getSchemaHash()).hasSize(64);
        assertThat(response.latestVersion()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateTenantNamespaceKeyAndKind() throws Exception {
        when(structureMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_STRUCTURE_ALREADY_EXISTS");
        verify(structureMapper, never()).insert(any(OpenApiStructure.class));
    }

    @Test
    void rejectsCrossTenantCreation() throws Exception {
        CreateStructureRequest request = validRequest();
        request.setTenantId("tenant-b");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        verify(structureMapper, never()).selectCount(any());
    }

    @Test
    void hidesCrossTenantStructureAsNotFound() {
        OpenApiStructure structure = new OpenApiStructure();
        structure.setId("structure-1");
        structure.setTenantId("tenant-b");
        when(structureMapper.selectById("structure-1")).thenReturn(structure);

        assertThatThrownBy(() -> service.getById("structure-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_STRUCTURE_NOT_FOUND");
        verify(versionMapper, never()).selectOne(any());
    }

    @Test
    void pinsTenantExtensionToPublishedCanonicalParent() throws Exception {
        CreateStructureRequest request = validRequest();
        request.setStructureKind(StructureKind.TENANT_EXTENSION);
        request.setParentStructureVersionId("parent-version");
        StructureVersion parentVersion = new StructureVersion();
        parentVersion.setId("parent-version");
        parentVersion.setStructureId("parent-structure");
        parentVersion.setLifecycleState(com.triobase.service.openapi.domain.enums.VersionLifecycleState.PUBLISHED);
        parentVersion.setSchemaContent(OBJECT_MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}"));
        OpenApiStructure parentStructure = new OpenApiStructure();
        parentStructure.setId("parent-structure");
        parentStructure.setStructureKind(StructureKind.CANONICAL);
        when(versionMapper.selectById("parent-version")).thenReturn(parentVersion);
        when(structureMapper.selectById("parent-structure")).thenReturn(parentStructure);
        when(structureMapper.selectCount(any())).thenReturn(0L);

        service.create(request);

        ArgumentCaptor<StructureVersion> versionCaptor = ArgumentCaptor.forClass(StructureVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        verify(tenantExtensionValidator).validate(
                parentVersion.getSchemaContent(), request.getSchemaContent(), "tenant-a");
        assertThat(versionCaptor.getValue().getParentStructureVersionId()).isEqualTo("parent-version");
    }

    private CreateStructureRequest validRequest() throws Exception {
        CreateStructureRequest request = new CreateStructureRequest();
        request.setNamespace("purchase");
        request.setStructureKey("purchase-order");
        request.setDisplayName("Purchase order");
        request.setStructureKind(StructureKind.CANONICAL);
        request.setDirection(StructureDirection.BIDIRECTIONAL);
        request.setOwnerType("DOMAIN");
        request.setOwnerId("procurement");
        request.setSchemaContent(OBJECT_MAPPER.readTree("{\"type\":\"object\"}"));
        return request;
    }
}
