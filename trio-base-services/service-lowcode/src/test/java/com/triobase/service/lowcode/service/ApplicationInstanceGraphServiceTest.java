package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.NestedFormInstanceRequest;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.entity.LcFormInstanceRelation;
import com.triobase.service.lowcode.entity.LcFormRelation;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import com.triobase.service.lowcode.mapper.FormInstanceRelationMapper;
import com.triobase.service.lowcode.mapper.FormRelationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationInstanceGraphServiceTest {

    @Mock ApplicationVersionMapper versionMapper;
    @Mock FormDefinitionMapper definitionMapper;
    @Mock FormRelationMapper relationMapper;
    @Mock FormInstanceMapper instanceMapper;
    @Mock FormInstanceRelationMapper instanceRelationMapper;
    @Mock LowcodeFormDataValidator dataValidator;
    @Mock LowcodeAuthorizationService authorizationService;
    @Mock FormInstanceService formInstanceService;
    ApplicationInstanceGraphService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationInstanceGraphService(versionMapper, definitionMapper, relationMapper,
                instanceMapper, instanceRelationMapper, dataValidator, authorizationService,
                formInstanceService, new ObjectMapper());
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U1", "tester", "default", List.of("USER"), List.of(), List.of(),
                null, null, null, null, null, null));
        LcApplicationVersion version = new LcApplicationVersion();
        version.setId("APPV1");
        version.setTenantId("default");
        version.setStatus("PUBLISHED");
        version.setPrimaryFormDefinitionId("MASTER");
        when(versionMapper.selectById("APPV1")).thenReturn(version);
        when(definitionMapper.selectById(any())).thenAnswer(invocation -> form((String) invocation.getArgument(0)));
        when(authorizationService.requireFormDecision(any(), any(), any(), any()))
                .thenReturn(new AuthorizationDecisionResponse());
        when(authorizationService.allowsCreate(any())).thenReturn(true);
        when(formInstanceService.getById(any())).thenAnswer(invocation -> {
            FormInstanceResponse response = new FormInstanceResponse();
            response.setId((String) invocation.getArgument(0));
            return response;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clear();
    }

    @Test
    void savesMasterAndChildWithRelationLink() {
        LcFormRelation relation = new LcFormRelation();
        relation.setRelationCode("DETAILS");
        relation.setParentFormDefinitionId("MASTER");
        relation.setChildFormDefinitionId("CHILD");
        relation.setChildForeignKeyField("parentId");
        relation.setCardinality("MANY");
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));

        NestedFormInstanceRequest child = node("CHILD", Map.of("line", "day1"), Map.of());
        NestedFormInstanceRequest root = node("MASTER", Map.of("reason", "leave"), Map.of("DETAILS", List.of(child)));

        var result = service.submit("APPV1", root);

        assertThat(result.getChildren().get("DETAILS")).hasSize(1);
        verify(instanceMapper, org.mockito.Mockito.times(2)).insert(any(LcFormInstance.class));
        verify(instanceRelationMapper).insert(any(LcFormInstanceRelation.class));
    }

    private NestedFormInstanceRequest node(String formId, Map<String, Object> data,
                                           Map<String, List<NestedFormInstanceRequest>> children) {
        NestedFormInstanceRequest request = new NestedFormInstanceRequest();
        request.setFormDefinitionId(formId);
        request.setData(data);
        request.setChildren(children);
        return request;
    }

    private LcFormDefinition form(String id) {
        LcFormDefinition form = new LcFormDefinition();
        form.setId(id);
        form.setFormKey(id.toLowerCase());
        form.setVersion(1);
        form.setStatus("PUBLISHED");
        form.setSchemaJson("{\"type\":\"object\",\"properties\":{}}");
        return form;
    }
}
