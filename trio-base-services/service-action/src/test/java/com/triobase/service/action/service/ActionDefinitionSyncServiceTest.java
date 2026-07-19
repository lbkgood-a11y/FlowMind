package com.triobase.service.action.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.service.action.dto.ActionDefinitionSyncRequest;
import com.triobase.service.action.dto.ActionDefinitionSyncResponse;
import com.triobase.service.action.entity.ActionDefinitionSnapshot;
import com.triobase.service.action.mapper.ActionDefinitionSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDefinitionSyncServiceTest {

    private final ActionDefinitionRegistry registry = new ActionDefinitionRegistry(List.of());
    private final ActionDefinitionSnapshotMapper snapshotMapper = mock(ActionDefinitionSnapshotMapper.class);
    private final ActionDefinitionSyncService service = new ActionDefinitionSyncService(
            registry,
            snapshotMapper,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void syncRegistersDefinitionAndPersistsSnapshot() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of());
        ActionDefinition definition = new ActionDefinition();
        definition.setActionType("scm.purchaseOrder.submit");
        definition.setOwnerService("service-scm");
        definition.setTargetType("SCM_PURCHASE_ORDER");
        definition.setExecutionMode(ActionExecutionMode.WORKFLOW);
        definition.setTargetStatus("SUBMITTED");
        definition.getDefaultRefreshScopes().addAll(List.of("document", "actions", "timeline"));

        ActionDefinitionSyncRequest request = new ActionDefinitionSyncRequest();
        request.setOwnerService("service-scm");
        request.setDefinitions(List.of(definition));

        ActionDefinitionSyncResponse response = service.sync(request);

        assertThat(response.getSynchronizedCount()).isEqualTo(1);
        assertThat(response.getVersions()).containsEntry("scm.purchaseOrder.submit", 1);
        assertThat(registry.find("scm.purchaseOrder.submit")).isPresent();
        verify(snapshotMapper).insert(any(ActionDefinitionSnapshot.class));
    }
}
