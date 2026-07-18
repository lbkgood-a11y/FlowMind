package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.workflow.client.LowcodeFormClient;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessPackageBusinessClosureSnapshotTest {

    private final ProcessPackageMapper processPackageMapper = mock(ProcessPackageMapper.class);
    private final LowcodeFormClient lowcodeFormClient = mock(LowcodeFormClient.class);
    private final ProcessDefinitionValidator processDefinitionValidator = mock(ProcessDefinitionValidator.class);
    private final FormSnapshotValidator formSnapshotValidator = mock(FormSnapshotValidator.class);
    private final BusinessClosurePlanCompiler businessClosurePlanCompiler =
            mock(BusinessClosurePlanCompiler.class);
    private final ProcessPackageService service = new ProcessPackageService(
            processPackageMapper,
            new ObjectMapper(),
            lowcodeFormClient,
            processDefinitionValidator,
            formSnapshotValidator,
            businessClosurePlanCompiler);

    @Test
    void publishStoresBusinessClosureSnapshotPerVersion() {
        ProcessPackage versionOne = draftPackage("PKG001", 1);
        ProcessPackage versionTwo = draftPackage("PKG002", 2);
        when(processPackageMapper.selectById("PKG001")).thenReturn(versionOne);
        when(processPackageMapper.selectById("PKG002")).thenReturn(versionTwo);
        when(processDefinitionValidator.validate(anyString()))
                .thenReturn(new com.triobase.service.workflow.dto.ProcessPackageDefinition());
        when(businessClosurePlanCompiler.compile(any()))
                .thenReturn(new BusinessClosurePlanCompiler.CompiledBusinessClosurePlan(
                        "{\"displayName\":\"报销单\"}",
                        null,
                        null,
                        "{\"action\":{\"displayName\":\"更新报销单状态\"}}",
                        null))
                .thenReturn(new BusinessClosurePlanCompiler.CompiledBusinessClosurePlan(
                        "{\"displayName\":\"费用单\"}",
                        null,
                        null,
                        "{\"action\":{\"displayName\":\"更新费用单状态\"}}",
                        null));

        var publishedOne = service.publish("PKG001");
        var publishedTwo = service.publish("PKG002");

        assertEquals("{\"displayName\":\"报销单\"}", publishedOne.getBusinessBindingSnapshot());
        assertEquals("{\"action\":{\"displayName\":\"更新报销单状态\"}}",
                publishedOne.getClosurePlanJson());
        assertEquals("{\"displayName\":\"费用单\"}", publishedTwo.getBusinessBindingSnapshot());
        assertEquals("{\"action\":{\"displayName\":\"更新费用单状态\"}}",
                publishedTwo.getClosurePlanJson());
    }

    @Test
    void hasPublishedPackageChecksPublishedProcessTarget() {
        when(processPackageMapper.selectCount(any())).thenReturn(1L);

        assertEquals(true, service.hasPublishedPackage("expense_report", null));
    }

    @Test
    void hasPublishedPackageRejectsBlankProcessKey() {
        assertEquals(false, service.hasPublishedPackage(" ", null));
    }

    @Test
    void publishedFormSnapshotDtoIgnoresFutureFields() throws Exception {
        PublishedFormSnapshotResponse snapshot = new ObjectMapper().readValue("""
                {
                  "formDefinitionId": "FORM001",
                  "tenantId": "TENANT_A",
                  "formKey": "expense",
                  "version": 3,
                  "schemaHash": "sha256:abc",
                  "schemaJson": "{\\"type\\":\\"object\\",\\"properties\\":{\\"amount\\":{\\"type\\":\\"number\\"}}}",
                  "uiSchemaJson": "{\\"amount\\":{\\"ui:widget\\":\\"money\\"}}",
                  "publishedBy": "designer-1",
                  "diagnostics": {"source":"lowcode"}
                }
                """, PublishedFormSnapshotResponse.class);

        assertEquals("FORM001", snapshot.getFormDefinitionId());
        assertEquals("TENANT_A", snapshot.getTenantId());
        assertEquals("expense", snapshot.getFormKey());
        assertEquals(3, snapshot.getVersion());
        assertEquals("sha256:abc", snapshot.getSchemaHash());
    }

    @Test
    void publishToleratesExtendedPublishedLowcodeSnapshotFields() {
        ProcessPackage draft = draftPackage("PKG003", 3);
        draft.setFormDefinitionId("FORM001");
        when(processPackageMapper.selectById("PKG003")).thenReturn(draft);
        when(processDefinitionValidator.validate(anyString()))
                .thenReturn(new com.triobase.service.workflow.dto.ProcessPackageDefinition());
        when(businessClosurePlanCompiler.compile(any()))
                .thenReturn(new BusinessClosurePlanCompiler.CompiledBusinessClosurePlan(
                        null,
                        null,
                        null,
                        null,
                        null));
        when(lowcodeFormClient.getPublishedForm("FORM001"))
                .thenReturn(R.ok(publishedForm("FORM001", "expense", 3,
                        "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}",
                        "{\"amount\":{\"ui:widget\":\"money\"}}")));

        var published = service.publish("PKG003");

        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(3, published.getFormDefinitionVersion());
        assertEquals("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}",
                published.getFormSchema());
    }

    @Test
    void publishRejectsDraftOrOfflineLowcodeFormReferences() {
        ProcessPackage draft = draftPackage("PKG004", 4);
        draft.setFormDefinitionId("FORM_DRAFT");
        when(processPackageMapper.selectById("PKG004")).thenReturn(draft);
        when(processDefinitionValidator.validate(anyString()))
                .thenReturn(new com.triobase.service.workflow.dto.ProcessPackageDefinition());
        when(lowcodeFormClient.getPublishedForm("FORM_DRAFT"))
                .thenReturn(R.fail(40901, "FORM_DEFINITION_NOT_PUBLISHED"));

        BizException exception = assertThrows(BizException.class,
                () -> service.publish("PKG004"));

        assertEquals(40901, exception.getCode());
        assertEquals("FORM_DEFINITION_NOT_PUBLISHED", exception.getMessage());
        assertEquals("DRAFT", draft.getStatus());
    }

    private ProcessPackage draftPackage(String id, int version) {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId(id);
        pkg.setProcessKey("expense_report");
        pkg.setName("Expense Report");
        pkg.setCategory("approval");
        pkg.setVersion(version);
        pkg.setStatus("DRAFT");
        pkg.setProcessJson("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"}
                ]}}
                """);
        return pkg;
    }

    private PublishedFormSnapshotResponse publishedForm(String id,
                                                        String formKey,
                                                        int version,
                                                        String schemaJson,
                                                        String uiSchemaJson) {
        PublishedFormSnapshotResponse response = new PublishedFormSnapshotResponse();
        response.setFormDefinitionId(id);
        response.setTenantId("TENANT_A");
        response.setFormKey(formKey);
        response.setVersion(version);
        response.setSchemaHash("sha256:" + version);
        response.setSchemaJson(schemaJson);
        response.setUiSchemaJson(uiSchemaJson);
        response.setPublishedAt(LocalDateTime.now());
        return response;
    }
}
