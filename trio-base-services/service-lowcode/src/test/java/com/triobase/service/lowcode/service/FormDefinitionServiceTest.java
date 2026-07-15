package com.triobase.service.lowcode.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class FormDefinitionServiceTest {

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    @InjectMocks
    private FormDefinitionService service;

    @Test
    void getPublishedSnapshotReturnsImmutableFormData() {
        LcFormDefinition definition = definition("PUBLISHED");
        when(formDefinitionMapper.selectById("FORM001")).thenReturn(definition);

        var response = service.getPublishedSnapshot("FORM001");

        assertEquals("FORM001", response.getFormDefinitionId());
        assertEquals(1, response.getVersion());
        assertEquals("{\"type\":\"object\"}", response.getSchemaJson());
    }

    @Test
    void getPublishedSnapshotRejectsDraft() {
        when(formDefinitionMapper.selectById("FORM001")).thenReturn(definition("DRAFT"));

        BizException exception = assertThrows(BizException.class,
                () -> service.getPublishedSnapshot("FORM001"));

        assertEquals("FORM_DEFINITION_NOT_PUBLISHED", exception.getMessage());
    }

    @Test
    void listPublishedDataResourcesBuildsStableResourceCode() {
        LcFormDefinition definition = definition("PUBLISHED");
        definition.setName("费用报销");
        when(formDefinitionMapper.selectList(any())).thenReturn(List.of(definition));

        var resources = service.listPublishedDataResources();

        assertEquals(1, resources.size());
        assertEquals("FORM:EXPENSE", resources.get(0).getResourceCode());
        assertEquals("费用报销", resources.get(0).getResourceName());
        assertEquals("LOWCODE_FORM", resources.get(0).getResourceType());
        assertEquals("FORM001", resources.get(0).getBusinessObjectId());
    }

    private LcFormDefinition definition(String status) {
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId("FORM001");
        definition.setFormKey("expense");
        definition.setVersion(1);
        definition.setStatus(status);
        definition.setSchemaJson("{\"type\":\"object\"}");
        definition.setUiSchemaJson("{}");
        return definition;
    }
}
