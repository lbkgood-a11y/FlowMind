package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.entity.StructureProvenance;
import com.triobase.service.openapi.dto.ImportOpenApiRequest;
import com.triobase.service.openapi.dto.StructureResponse;
import com.triobase.service.openapi.infrastructure.mapper.StructureProvenanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenApiImportServiceTest {

    @Mock
    private StructureRegistryService structureRegistryService;
    @Mock
    private StructureProvenanceMapper provenanceMapper;
    @Mock
    private IntegrationAuditService auditService;

    private OpenApiImportService service;

    @BeforeEach
    void setUp() {
        service = new OpenApiImportService(
                structureRegistryService, provenanceMapper, new ObjectMapper(), auditService);
    }

    @Test
    void importsComponentsRequestsAndResponsesFromYaml() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Partner API
                  version: 1.0.0
                components:
                  schemas:
                    Order:
                      type: object
                      properties:
                        id:
                          type: string
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      requestBody:
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/Order'
                      responses:
                        '200':
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  accepted:
                                    type: boolean
                """;
        AtomicInteger sequence = new AtomicInteger();
        when(structureRegistryService.create(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, com.triobase.service.openapi.dto.CreateStructureRequest.class);
            int number = sequence.incrementAndGet();
            return new StructureResponse(
                    "structure-" + number, "tenant-a", request.getNamespace(), request.getStructureKey(),
                    request.getDisplayName(), request.getDescription(), request.getStructureKind(),
                    request.getDirection(), request.getOwnerType(), request.getOwnerId(),
                    AssetLifecycleState.ACTIVE, 1, "version-" + number, VersionLifecycleState.DRAFT,
                    LocalDateTime.now(), LocalDateTime.now());
        });

        var result = service.importAll(request(yaml));

        assertThat(result.openApiVersion()).isEqualTo("3.0.3");
        assertThat(result.structures()).hasSize(3);
        assertThat(result.structures()).extracting(StructureResponse::direction)
                .contains(StructureDirection.BIDIRECTIONAL, StructureDirection.REQUEST, StructureDirection.RESPONSE);
        verify(provenanceMapper, org.mockito.Mockito.times(3)).insert(any(StructureProvenance.class));
    }

    @Test
    void rejectsUnresolvedReference() {
        String json = """
                {
                  "openapi":"3.1.0",
                  "info":{"title":"x","version":"1"},
                  "components":{"schemas":{"Order":{"$ref":"#/components/schemas/Missing"}}},
                  "paths":{}
                }
                """;

        assertThatThrownBy(() -> service.importAll(request(json)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("REFERENCE_UNRESOLVED");
    }

    private ImportOpenApiRequest request(String document) {
        return new ImportOpenApiRequest(
                document.getBytes(StandardCharsets.UTF_8),
                "partner-api.yaml",
                "tenant-a",
                "partner",
                StructureKind.EXTERNAL,
                "DOMAIN",
                "integration-team");
    }
}
