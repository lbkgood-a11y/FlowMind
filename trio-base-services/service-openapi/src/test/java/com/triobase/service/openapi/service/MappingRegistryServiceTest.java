package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.MappingRule;
import com.triobase.service.openapi.domain.entity.MappingSet;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.MappingDirection;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateMappingSetRequest;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingSetMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingRegistryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Mock private MappingSetMapper setMapper;
    @Mock private MappingVersionMapper versionMapper;
    @Mock private MappingRuleMapper ruleMapper;
    @Mock private StructureVersionMapper structureVersionMapper;
    @Mock private MappingDefinitionValidator validator;
    @Mock private IntegrationAuditService auditService;
    @Mock private MappingPlanCompiler planCompiler;
    @Mock private MappingContractTestService contractTestService;
    private MappingRegistryService service;

    @BeforeEach
    void setUp() {
        service = new MappingRegistryService(
                setMapper, versionMapper, ruleMapper, structureVersionMapper,
                validator, auditService, planCompiler, contractTestService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsDirectionalDraftPinnedToPublishedStructures() throws Exception {
        when(setMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(structureVersionMapper.selectById("canonical-v1"))
                .thenReturn(structureVersion("canonical-v1", "canonical", VersionLifecycleState.PUBLISHED));
        when(structureVersionMapper.selectById("external-v1"))
                .thenReturn(structureVersion("external-v1", "external", VersionLifecycleState.PUBLISHED));
        when(validator.validate(any(), any(), any())).thenReturn(
                OBJECT_MAPPER.readTree("{\"valid\":true}"));

        var response = service.create(request());

        assertThat(response.direction()).isEqualTo(MappingDirection.CANONICAL_TO_EXTERNAL);
        assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);
        verify(setMapper).insert(any(MappingSet.class));
        verify(versionMapper).insert(any(MappingVersion.class));
        verify(ruleMapper).insert(any(MappingRule.class));
    }

    @Test
    void rejectsDirectionStructureMismatch() {
        when(setMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(structureVersionMapper.selectById("canonical-v1"))
                .thenReturn(structureVersion("canonical-v1", "external", VersionLifecycleState.PUBLISHED));
        when(structureVersionMapper.selectById("external-v1"))
                .thenReturn(structureVersion("external-v1", "canonical", VersionLifecycleState.PUBLISHED));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_MAPPING_DIRECTION_STRUCTURE_MISMATCH");
    }

    private CreateMappingSetRequest request() {
        return new CreateMappingSetRequest(
                null, "order-to-erp", "Order to ERP", null,
                MappingDirection.CANONICAL_TO_EXTERNAL,
                "canonical", "external", "canonical-v1", "external-v1", "integration-team",
                List.of(new MappingRuleRequest(
                        1, MappingOperation.COPY, "/id", "/externalId",
                        OBJECT_MAPPER.createObjectNode(), true)));
    }

    private StructureVersion structureVersion(String id, String structureId, VersionLifecycleState state) {
        StructureVersion version = new StructureVersion();
        version.setId(id);
        version.setStructureId(structureId);
        version.setLifecycleState(state);
        version.setSchemaContent(OBJECT_MAPPER.createObjectNode().put("type", "object")
                .set("properties", OBJECT_MAPPER.createObjectNode()));
        return version;
    }
}
