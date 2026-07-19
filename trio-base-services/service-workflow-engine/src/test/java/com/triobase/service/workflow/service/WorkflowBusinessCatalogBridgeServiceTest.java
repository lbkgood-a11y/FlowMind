package com.triobase.service.workflow.service;

import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowBusinessCatalogBridgeServiceTest {

    private final WorkflowBusinessCatalogBridgeService bridge =
            new WorkflowBusinessCatalogBridgeService(mock(BusinessObjectCatalogService.class));

    @Test
    void mapsWorkflowCatalogToSharedMetadata() {
        BusinessObjectCatalogResponse response = new BusinessObjectCatalogResponse();
        response.setObject(new BusinessObjectSummaryResponse(
                "obj-1",
                "GLOBAL",
                "expense_report",
                "费用报销",
                "service-workflow-engine",
                1,
                "PUBLISHED",
                "demo"));
        BusinessObjectCatalogResponse.StatusItem status = new BusinessObjectCatalogResponse.StatusItem();
        status.setStatusCode("DRAFT");
        status.setStatusGroup("DRAFT");
        status.setInitial(true);
        response.getStatuses().add(status);
        BusinessObjectCatalogResponse.ActionItem action = new BusinessObjectCatalogResponse.ActionItem();
        action.setActionCode("SUBMIT");
        action.setActionType("process.instance.start");
        action.setModeDefault("WORKFLOW");
        response.getActions().add(action);

        BusinessObjectMetadata metadata = bridge.toSharedMetadata(response);

        assertThat(metadata.getObjectType()).isEqualTo("expense_report");
        assertThat(metadata.getStatuses()).hasSize(1);
        assertThat(metadata.getActions().getFirst().getActionType()).isEqualTo("process.instance.start");
    }
}
