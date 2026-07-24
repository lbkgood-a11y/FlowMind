package com.triobase.service.lowcode.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.FormRelationRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormRelationGraphValidatorTest {

    @Mock private FormDefinitionMapper formMapper;
    @Mock private FormFieldDefinitionMapper fieldMapper;
    private FormRelationGraphValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FormRelationGraphValidator(formMapper, fieldMapper);
        when(formMapper.selectById(any())).thenAnswer(invocation -> form((String) invocation.getArgument(0)));
        when(fieldMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void acceptsMasterChildGrandchild() {
        assertDoesNotThrow(() -> validator.validate("default", "A", List.of(
                relation("AB", "A", "B"), relation("BC", "B", "C"))));
    }

    @Test
    void rejectsFourthLevelAndCycle() {
        BizException depth = assertThrows(BizException.class, () -> validator.validate("default", "A", List.of(
                relation("AB", "A", "B"), relation("BC", "B", "C"), relation("CD", "C", "D"))));
        assertEquals("FORM_RELATION_DEPTH_EXCEEDED", depth.getMessage());

        BizException cycle = assertThrows(BizException.class, () -> validator.validate("default", "A", List.of(
                relation("AB", "A", "B"), relation("BA", "B", "A"))));
        assertEquals("FORM_RELATION_CYCLE", cycle.getMessage());
    }

    private FormRelationRequest relation(String code, String parent, String child) {
        FormRelationRequest request = new FormRelationRequest();
        request.setRelationCode(code);
        request.setParentFormDefinitionId(parent);
        request.setChildFormDefinitionId(child);
        request.setCardinality("MANY");
        request.setParentKeyField("id");
        request.setChildForeignKeyField("parentId");
        return request;
    }

    private LcFormDefinition form(String id) {
        LcFormDefinition form = new LcFormDefinition();
        form.setId(id);
        form.setTenantId("default");
        form.setStatus("PUBLISHED");
        return form;
    }
}
