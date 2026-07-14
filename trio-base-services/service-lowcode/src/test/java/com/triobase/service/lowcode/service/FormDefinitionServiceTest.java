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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
