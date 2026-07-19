package com.triobase.service.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.catalog.BusinessActionMetadata;
import com.triobase.common.dto.catalog.BusinessObjectManifest;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.common.dto.catalog.BusinessStatusMetadata;
import com.triobase.service.catalog.entity.BusinessObjectRecord;
import com.triobase.service.catalog.mapper.BusinessObjectRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessCatalogServiceTest {

    private final BusinessObjectRecordMapper mapper = mock(BusinessObjectRecordMapper.class);
    private final BusinessCatalogService service = new BusinessCatalogService(
            mapper,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void syncStoresManifestProjection() {
        BusinessObjectManifest manifest = purchaseOrderManifest();
        when(mapper.selectOne(any())).thenReturn(null);

        BusinessObjectMetadata metadata = service.sync(manifest);

        assertThat(metadata.getObjectType()).isEqualTo("SCM_PURCHASE_ORDER");
        assertThat(metadata.getStatuses()).hasSize(1);
        assertThat(metadata.getActions()).hasSize(1);
        verify(mapper).insert(any(BusinessObjectRecord.class));
    }

    @Test
    void rejectsInvalidManifest() {
        BusinessObjectManifest manifest = new BusinessObjectManifest();
        manifest.setObjectType("SCM_PURCHASE_ORDER");

        assertThatThrownBy(() -> service.sync(manifest))
                .isInstanceOf(BizException.class);
    }

    @Test
    void tenantOfflineRecordRemovesGlobalObject() {
        BusinessObjectRecord global = record("GLOBAL", "PUBLISHED", 1);
        BusinessObjectRecord offline = record("tenant-a", "OFFLINE", 1);
        when(mapper.selectList(any()))
                .thenReturn(List.of(global))
                .thenReturn(List.of(offline));

        List<BusinessObjectMetadata> objects = service.listEffective("tenant-a");

        assertThat(objects).isEmpty();
    }

    private BusinessObjectManifest purchaseOrderManifest() {
        BusinessObjectManifest manifest = new BusinessObjectManifest();
        manifest.setTenantId("GLOBAL");
        manifest.setObjectType("SCM_PURCHASE_ORDER");
        manifest.setDisplayName("采购订单");
        manifest.setOwnerService("service-scm");
        BusinessStatusMetadata status = new BusinessStatusMetadata();
        status.setStatusCode("DRAFT");
        status.setStatusGroup("DRAFT");
        status.setInitial(true);
        manifest.getStatuses().add(status);
        BusinessActionMetadata action = new BusinessActionMetadata();
        action.setActionCode("SUBMIT");
        action.setActionType("scm.purchaseOrder.submit");
        action.setDisplayName("提交");
        manifest.getActions().add(action);
        return manifest;
    }

    private BusinessObjectRecord record(String tenantId, String status, int version) {
        BusinessObjectRecord record = new BusinessObjectRecord();
        record.setTenantId(tenantId);
        record.setObjectType("SCM_PURCHASE_ORDER");
        record.setDisplayName("采购订单");
        record.setOwnerService("service-scm");
        record.setVersion(version);
        record.setLifecycleStatus(status);
        record.setStatusesJson("[]");
        record.setActionsJson("[]");
        record.setFieldsJson("[]");
        record.setPageJson("{}");
        record.setAttributesJson("{}");
        return record;
    }
}
