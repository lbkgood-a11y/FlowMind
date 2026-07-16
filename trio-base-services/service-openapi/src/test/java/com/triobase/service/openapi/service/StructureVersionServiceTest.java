package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.PublicationApproval;
import com.triobase.service.openapi.infrastructure.mapper.OpenApiStructureMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureFieldMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructureVersionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OpenApiStructureMapper structureMapper;
    @Mock
    private StructureVersionMapper versionMapper;
    @Mock
    private StructureFieldMapper fieldMapper;
    @Mock
    private StructureSchemaInspector schemaInspector;
    @Mock
    private SchemaCompatibilityAnalyzer compatibilityAnalyzer;
    @Mock
    private IntegrationAuditService auditService;

    private StructureVersionService service;

    @BeforeEach
    void setUp() {
        service = new StructureVersionService(
                structureMapper, versionMapper, fieldMapper, schemaInspector, compatibilityAnalyzer, auditService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of("TENANT_ADMIN"), List.of(), 1L, 1L, 1L));
        org.mockito.Mockito.lenient().when(schemaInspector.inspect(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsNextDraftVersion() throws Exception {
        when(structureMapper.selectById("structure-1")).thenReturn(structure("tenant-a"));
        when(versionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        StructureVersion latest = version(3, VersionLifecycleState.PUBLISHED);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(latest);

        var response = service.createDraft(
                "structure-1", OBJECT_MAPPER.readTree("{\"type\":\"object\"}"), "next");

        assertThat(response.versionNumber()).isEqualTo(4);
        assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);
        verify(versionMapper).insert(any(StructureVersion.class));
    }

    @Test
    void preventsMutationOfPublishedVersion() throws Exception {
        StructureVersion published = version(1, VersionLifecycleState.PUBLISHED);
        when(versionMapper.selectById("version-1")).thenReturn(published);
        when(structureMapper.selectById("structure-1")).thenReturn(structure("tenant-a"));

        assertThatThrownBy(() -> service.updateDraft(
                "version-1", OBJECT_MAPPER.readTree("{\"type\":\"object\"}"), "change", null))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_PUBLISHED_STRUCTURE_VERSION_IMMUTABLE");
        verify(versionMapper, never()).updateById(any(StructureVersion.class));
    }

    @Test
    void publishesOnlyDraftAndRecordsPublisher() {
        StructureVersion draft = version(1, VersionLifecycleState.DRAFT);
        when(versionMapper.selectById("version-1")).thenReturn(draft);
        when(structureMapper.selectById("structure-1")).thenReturn(structure("tenant-a"));
        when(versionMapper.updateById(draft)).thenReturn(1);

        var response = service.publish("version-1");

        assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.PUBLISHED);
        assertThat(response.publishedBy()).isEqualTo("user-1");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void rejectsSecondDraft() throws Exception {
        when(structureMapper.selectById("structure-1")).thenReturn(structure("tenant-a"));
        when(versionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.createDraft(
                "structure-1", OBJECT_MAPPER.readTree("{\"type\":\"object\"}"), "next"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_STRUCTURE_DRAFT_ALREADY_EXISTS");
    }

    @Test
    void breakingPublicationRequiresDualApprovalAndNewCompatibilityLine() throws Exception {
        StructureVersion draft = version(2, VersionLifecycleState.DRAFT);
        draft.setSchemaContent(OBJECT_MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}"));
        draft.setSemanticChange(OBJECT_MAPPER.createObjectNode());
        StructureVersion previous = version(1, VersionLifecycleState.PUBLISHED);
        previous.setCompatibilityLine(1);
        previous.setSchemaContent(OBJECT_MAPPER.readTree(
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}"));
        when(versionMapper.selectById("version-1")).thenReturn(draft);
        when(structureMapper.selectById("structure-1")).thenReturn(structure("tenant-a"));
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(previous);
        when(compatibilityAnalyzer.analyze(any(), any(), any())).thenReturn(
                new SchemaCompatibilityAnalyzer.CompatibilityReport(
                        false, true, false, false,
                        List.of("FIELD_REMOVED:/id"), List.of(), List.of()));

        assertThatThrownBy(() -> service.publish("version-1", PublicationApproval.none()))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_STRUCTURE_PUBLICATION_DUAL_APPROVAL_REQUIRED");

        when(versionMapper.updateById(draft)).thenReturn(1);
        var response = service.publish(
                "version-1", new PublicationApproval(true, true, true));
        assertThat(response.compatibilityLine()).isEqualTo(2);
    }

    private OpenApiStructure structure(String tenantId) {
        OpenApiStructure structure = new OpenApiStructure();
        structure.setId("structure-1");
        structure.setTenantId(tenantId);
        return structure;
    }

    private StructureVersion version(int number, VersionLifecycleState state) {
        StructureVersion version = new StructureVersion();
        version.setId("version-1");
        version.setStructureId("structure-1");
        version.setVersionNumber(number);
        version.setLifecycleState(state);
        version.setCompatibilityLine(1);
        version.setRowVersion(0L);
        return version;
    }
}
