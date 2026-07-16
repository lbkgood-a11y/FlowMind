package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.OpenApiExportRequest;
import com.triobase.service.openapi.infrastructure.mapper.OpenApiStructureMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenApiExportServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private StructureVersionMapper versionMapper;
    @Mock private OpenApiStructureMapper structureMapper;
    @Mock private IntegrationAuditService auditService;
    private OpenApiExportService service;

    @BeforeEach
    void setUp() {
        service = new OpenApiExportService(versionMapper, structureMapper, OBJECT_MAPPER, auditService);
    }

    @Test
    void exportsPinnedPublishedContractsWithoutSecretMetadata() throws Exception {
        StructureVersion requestVersion = version("request-version", "request-structure");
        StructureVersion responseVersion = version("response-version", "response-structure");
        when(versionMapper.selectById("request-version")).thenReturn(requestVersion);
        when(versionMapper.selectById("response-version")).thenReturn(responseVersion);
        when(structureMapper.selectById("request-structure")).thenReturn(structure("request-structure", "order-request"));
        when(structureMapper.selectById("response-structure")).thenReturn(structure("response-structure", "order-response"));

        var document = service.export(new OpenApiExportRequest(
                "Order API", "1.0.0", "https://api.example.test",
                List.of(new OpenApiExportRequest.ExportOperation(
                        "/orders", "post", "createOrder", "request-version", "response-version"))));

        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(document.at("/paths/~1orders/post/requestBody/content/application~1json/schema/$ref").asText())
                .startsWith("#/components/schemas/");
        assertThat(document.toString()).doesNotContain("secretReference", "authorization");
    }

    @Test
    void rejectsDraftStructureExport() throws Exception {
        StructureVersion version = version("request-version", "request-structure");
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        when(versionMapper.selectById("request-version")).thenReturn(version);

        assertThatThrownBy(() -> service.export(new OpenApiExportRequest(
                "Order API", "1.0.0", null,
                List.of(new OpenApiExportRequest.ExportOperation(
                        "/orders", "post", "createOrder", "request-version", null)))))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_EXPORT_REQUIRES_PUBLISHED_STRUCTURE");
    }

    private StructureVersion version(String id, String structureId) throws Exception {
        StructureVersion version = new StructureVersion();
        version.setId(id);
        version.setStructureId(structureId);
        version.setVersionNumber(1);
        version.setCompatibilityLine(1);
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setSchemaContent(OBJECT_MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}"));
        return version;
    }

    private OpenApiStructure structure(String id, String key) {
        OpenApiStructure structure = new OpenApiStructure();
        structure.setId(id);
        structure.setNamespace("partner");
        structure.setStructureKey(key);
        return structure;
    }
}
