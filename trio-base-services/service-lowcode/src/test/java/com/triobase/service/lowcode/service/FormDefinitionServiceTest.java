package com.triobase.service.lowcode.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.CreateFormDefinitionRequest;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.dto.UpdateFormDefinitionRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormFieldDefinition;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.FormFieldDefinitionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class FormDefinitionServiceTest {

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    @Mock
    private LowcodeFormSchemaValidator formSchemaValidator;

    @InjectMocks
    private FormDefinitionService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

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

    @Test
    void createRejectsDuplicateDraftInTenant() {
        setTenantUser();
        when(formDefinitionMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(createRequest(), "alice"));

        assertEquals("FORM_KEY_ALREADY_EXISTS", exception.getMessage());
        verify(formDefinitionMapper, never()).insert(any(LcFormDefinition.class));
    }

    @Test
    void createAllowsSameFormKeyAcrossTenantsWhenTenantDuplicateAbsent() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U002", "bob", "tenant-b", List.of("ADMIN"), List.of(), null, null, null));
        AtomicReference<LcFormDefinition> inserted = new AtomicReference<>();
        when(formDefinitionMapper.selectCount(any())).thenReturn(0L);
        when(formDefinitionMapper.insert(any(LcFormDefinition.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(formDefinitionMapper.selectOne(any())).thenAnswer(invocation -> inserted.get());
        when(formFieldDefinitionMapper.selectList(any())).thenReturn(List.of());

        var response = service.create(createRequest(), "bob");

        assertEquals("tenant-b", response.getTenantId());
        assertEquals("expense", response.getFormKey());
        assertEquals(1, response.getVersion());
    }

    @Test
    void createRejectsInvalidSchemaFromValidator() {
        doThrow(new BizException(40010, "FORM_SCHEMA_INVALID_REQUIRED_FIELD"))
                .when(formSchemaValidator).validate(any(), any(), any());

        BizException exception = assertThrows(BizException.class,
                () -> service.create(createRequest(), "alice"));

        assertEquals("FORM_SCHEMA_INVALID_REQUIRED_FIELD", exception.getMessage());
        verify(formDefinitionMapper, never()).insert(any(LcFormDefinition.class));
    }

    @Test
    void updateRejectsUnsupportedWidgetFromValidator() {
        setTenantUser();
        LcFormDefinition definition = definition("DRAFT");
        when(formDefinitionMapper.selectOne(any())).thenReturn(definition);
        when(formFieldDefinitionMapper.selectList(any())).thenReturn(List.of());
        doThrow(new BizException(40010, "FORM_WIDGET_UNSUPPORTED"))
                .when(formSchemaValidator).validate(any(), any(), any());

        BizException exception = assertThrows(BizException.class,
                () -> service.update("FORM001", new UpdateFormDefinitionRequest(), "alice"));

        assertEquals("FORM_WIDGET_UNSUPPORTED", exception.getMessage());
        verify(formDefinitionMapper, never()).updateById(any(LcFormDefinition.class));
    }

    @Test
    void updateRejectsPublishedDefinition() {
        setTenantUser();
        LcFormDefinition definition = definition("PUBLISHED");
        when(formDefinitionMapper.selectOne(any())).thenReturn(definition);

        BizException exception = assertThrows(BizException.class,
                () -> service.update("FORM001", new com.triobase.service.lowcode.dto.UpdateFormDefinitionRequest(), "alice"));

        assertEquals("ONLY_DRAFT_CAN_BE_MODIFIED", exception.getMessage());
    }

    @Test
    void publishDraftValidatesAndRecordsSchemaHash() {
        setTenantUser();
        LcFormDefinition definition = definition("DRAFT");
        when(formDefinitionMapper.selectOne(any())).thenReturn(definition);
        when(formFieldDefinitionMapper.selectList(any())).thenReturn(List.of(fieldDefinition("amount")));

        var response = service.publish("FORM001");

        assertEquals("PUBLISHED", response.getStatus());
        verify(formSchemaValidator).validate(any(), any(), any());
        verify(formDefinitionMapper).updateById(definition);
    }

    @Test
    void offlinePublishedDefinition() {
        setTenantUser();
        LcFormDefinition definition = definition("PUBLISHED");
        when(formDefinitionMapper.selectOne(any())).thenReturn(definition);
        when(formFieldDefinitionMapper.selectList(any())).thenReturn(List.of());

        var response = service.offline("FORM001");

        assertEquals("OFFLINE", response.getStatus());
        verify(formDefinitionMapper).updateById(definition);
    }

    @Test
    void deriveNewVersionCreatesTenantDraftFromPublishedSource() {
        setTenantUser();
        LcFormDefinition source = definition("PUBLISHED");
        AtomicReference<LcFormDefinition> inserted = new AtomicReference<>();
        AtomicInteger selectOneCalls = new AtomicInteger();
        when(formDefinitionMapper.selectOne(any())).thenAnswer(invocation ->
                selectOneCalls.getAndIncrement() == 0 ? source : inserted.get());
        when(formDefinitionMapper.selectCount(any())).thenReturn(0L);
        when(formDefinitionMapper.selectList(any())).thenReturn(List.of(source));
        when(formFieldDefinitionMapper.selectList(any())).thenReturn(List.of());
        when(formDefinitionMapper.insert(any(LcFormDefinition.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        var response = service.deriveNewVersion("FORM001", "alice");

        assertEquals("DRAFT", response.getStatus());
        assertEquals(2, response.getVersion());
        assertEquals("FORM001", inserted.get().getSourceFormDefinitionId());
    }

    private LcFormDefinition definition(String status) {
        LcFormDefinition definition = new LcFormDefinition();
        definition.setId("FORM001");
        definition.setTenantId("tenant-a");
        definition.setFormKey("expense");
        definition.setName("Expense");
        definition.setVersion(1);
        definition.setStatus(status);
        definition.setSchemaHash("hash");
        definition.setSchemaJson("{\"type\":\"object\"}");
        definition.setUiSchemaJson("{}");
        return definition;
    }

    private CreateFormDefinitionRequest createRequest() {
        CreateFormDefinitionRequest request = new CreateFormDefinitionRequest();
        request.setFormKey("expense");
        request.setName("Expense");
        request.setSchemaJson("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}");
        request.setUiSchemaJson("{\"amount\":{\"ui:widget\":\"money\"}}");
        request.setFields(List.of(field("amount")));
        return request;
    }

    private FormFieldSchemaRequest field(String fieldKey) {
        FormFieldSchemaRequest field = new FormFieldSchemaRequest();
        field.setFieldKey(fieldKey);
        field.setLabel("Amount");
        field.setFieldType("number");
        field.setSortOrder(1);
        return field;
    }

    private LcFormFieldDefinition fieldDefinition(String fieldKey) {
        LcFormFieldDefinition field = new LcFormFieldDefinition();
        field.setTenantId("tenant-a");
        field.setFormDefinitionId("FORM001");
        field.setFieldKey(fieldKey);
        field.setLabel("Amount");
        field.setFieldType("number");
        field.setSortOrder(1);
        return field;
    }

    private void setTenantUser() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of("ADMIN"), List.of(), null, null, null));
    }
}
