package com.triobase.common.dto.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCatalogSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesBusinessObjectManifest() throws Exception {
        BusinessObjectManifest manifest = new BusinessObjectManifest();
        manifest.setTenantId("GLOBAL");
        manifest.setObjectType("SCM_PURCHASE_ORDER");
        manifest.setDisplayName("采购订单");
        manifest.setOwnerService("service-scm");

        BusinessStatusMetadata draft = new BusinessStatusMetadata();
        draft.setStatusCode("DRAFT");
        draft.setStatusGroup("DRAFT");
        draft.setInitial(true);
        manifest.getStatuses().add(draft);

        BusinessActionMetadata submit = new BusinessActionMetadata();
        submit.setActionCode("SUBMIT");
        submit.setActionType("scm.purchaseOrder.submit");
        submit.setDisplayName("提交");
        submit.setPrimary(true);
        submit.setRequiresConfirmation(true);
        submit.setExecutionMode("WORKFLOW");
        submit.getRefreshScopes().addAll(List.of("document", "actions", "timeline"));
        manifest.getActions().add(submit);

        BusinessFieldMetadata supplier = new BusinessFieldMetadata();
        supplier.setFieldKey("supplierId");
        supplier.setDisplayName("供应商");
        supplier.setRequired(true);
        manifest.getFields().add(supplier);

        String json = objectMapper.writeValueAsString(manifest);
        BusinessObjectManifest restored = objectMapper.readValue(json, BusinessObjectManifest.class);

        assertThat(restored.getObjectType()).isEqualTo("SCM_PURCHASE_ORDER");
        assertThat(restored.getStatuses()).hasSize(1);
        assertThat(restored.getActions().getFirst().getRefreshScopes())
                .containsExactly("document", "actions", "timeline");
        assertThat(restored.getFields().getFirst().isRequired()).isTrue();
    }

    @Test
    void serializesTimelineEntry() throws Exception {
        BusinessTimelineEntry entry = new BusinessTimelineEntry();
        entry.setEventId("evt-1");
        entry.setTargetType("SCM_PURCHASE_ORDER");
        entry.setTargetId("PO-1");
        entry.setActionId("act-1");
        entry.setTraceId("trace-1");
        entry.getSummary().put("changedFields", List.of("amount"));

        String json = objectMapper.writeValueAsString(entry);
        BusinessTimelineEntry restored = objectMapper.readValue(json, BusinessTimelineEntry.class);

        assertThat(restored.getActionId()).isEqualTo("act-1");
        assertThat(restored.getSummary()).containsKey("changedFields");
    }
}
