package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.workflow.client.LowcodeFormClient;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessPackageBusinessClosureSnapshotTest {

    private final ProcessPackageMapper processPackageMapper = mock(ProcessPackageMapper.class);
    private final ProcessDefinitionValidator processDefinitionValidator = mock(ProcessDefinitionValidator.class);
    private final FormSnapshotValidator formSnapshotValidator = mock(FormSnapshotValidator.class);
    private final BusinessClosurePlanCompiler businessClosurePlanCompiler =
            mock(BusinessClosurePlanCompiler.class);
    private final ProcessPackageService service = new ProcessPackageService(
            processPackageMapper,
            new ObjectMapper(),
            mock(LowcodeFormClient.class),
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
}
