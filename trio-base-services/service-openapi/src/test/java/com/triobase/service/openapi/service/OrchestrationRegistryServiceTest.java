package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OrchestrationDefinition;
import com.triobase.service.openapi.domain.entity.OrchestrationVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateOrchestrationRequest;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrchestrationRegistryServiceTest {

    @Mock private OrchestrationDefinitionMapper definitionMapper;
    @Mock private OrchestrationVersionMapper versionMapper;
    @Mock private IntegrationAuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrchestrationRegistryService service;

    @BeforeEach
    void setUp() {
        service = new OrchestrationRegistryService(definitionMapper, versionMapper,
                new OrchestrationDefinitionValidator(objectMapper), auditService, objectMapper);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "owner-1", "Owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsValidatedDraftAndPublishesImmutableVersion() throws Exception {
        JsonNode definitionJson = validDefinition();
        when(definitionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        ArgumentCaptor<OrchestrationDefinition> definitionCaptor =
                ArgumentCaptor.forClass(OrchestrationDefinition.class);
        ArgumentCaptor<OrchestrationVersion> versionCaptor =
                ArgumentCaptor.forClass(OrchestrationVersion.class);
        when(definitionMapper.insert(definitionCaptor.capture())).thenReturn(1);
        when(versionMapper.insert(versionCaptor.capture())).thenReturn(1);

        var draft = service.create(new CreateOrchestrationRequest(
                null, "orders.submit", "Submit order", "owner-1", "1", definitionJson));

        assertThat(draft.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);
        assertThat(draft.definitionHash()).isNotBlank();
        assertThat(draft.validationResult().path("valid").asBoolean()).isTrue();

        OrchestrationDefinition storedDefinition = definitionCaptor.getValue();
        OrchestrationVersion storedVersion = versionCaptor.getValue();
        when(versionMapper.selectById(storedVersion.getId())).thenReturn(storedVersion);
        when(definitionMapper.selectById(storedDefinition.getId())).thenReturn(storedDefinition);
        when(versionMapper.updateById(any(OrchestrationVersion.class))).thenReturn(1);

        var published = service.publish(storedVersion.getId());

        assertThat(published.lifecycleState()).isEqualTo(VersionLifecycleState.PUBLISHED);
        assertThat(published.publishedBy()).isEqualTo("owner-1");
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("ORCHESTRATION_CREATED"),
                org.mockito.ArgumentMatchers.eq("ORCHESTRATION"), any(), any());
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("ORCHESTRATION_PUBLISHED"),
                org.mockito.ArgumentMatchers.eq("ORCHESTRATION_VERSION"), any(), any());
        assertThatThrownBy(() -> service.updateDraft(storedVersion.getId(),
                new com.triobase.service.openapi.dto.OrchestrationVersionMutationRequest(
                        "1", definitionJson)))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_PUBLISHED_ORCHESTRATION_IMMUTABLE");
    }

    private JsonNode validDefinition() throws Exception {
        return objectMapper.readTree("""
                {"schemaVersion":"1","start":"invoke","steps":[
                  {"key":"invoke","type":"INVOKE","connectorVersionId":"connector-v1","next":"end"},
                  {"key":"end","type":"END"}
                ]}
                """);
    }
}
