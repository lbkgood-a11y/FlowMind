package com.triobase.service.workflow.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.entity.BusinessObject;
import com.triobase.service.workflow.mapper.BusinessObjectActionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectAgentActionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectEventMapper;
import com.triobase.service.workflow.mapper.BusinessObjectFormMapper;
import com.triobase.service.workflow.mapper.BusinessObjectMapper;
import com.triobase.service.workflow.mapper.BusinessObjectPermissionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectStatusMapper;
import com.triobase.service.workflow.mapper.BusinessObjectTemplateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessObjectCatalogServiceTest {

    private final BusinessObjectMapper businessObjectMapper = mock(BusinessObjectMapper.class);
    private final BusinessObjectCatalogService service = new BusinessObjectCatalogService(
            businessObjectMapper,
            mock(BusinessObjectStatusMapper.class),
            mock(BusinessObjectFormMapper.class),
            mock(BusinessObjectPermissionMapper.class),
            mock(BusinessObjectActionMapper.class),
            mock(BusinessObjectEventMapper.class),
            mock(BusinessObjectAgentActionMapper.class),
            mock(BusinessObjectTemplateMapper.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void tenantPublishedObjectOverridesGlobalObject() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "Alice", "TENANT_A", List.of(), List.of(), null, null, null));
        when(businessObjectMapper.selectList(any()))
                .thenReturn(List.of(
                        object("global-expense", "GLOBAL", "expense_report", "报销单", "PUBLISHED", 1),
                        object("global-ticket", "GLOBAL", "ticket", "工单", "PUBLISHED", 1)))
                .thenReturn(List.of(
                        object("tenant-expense", "TENANT_A", "expense_report", "租户报销单", "PUBLISHED", 1)));

        List<BusinessObjectSummaryResponse> result = service.listPublishedForCurrentTenant();

        assertEquals(2, result.size());
        assertEquals("租户报销单", result.stream()
                .filter(item -> "expense_report".equals(item.getTypeCode()))
                .findFirst().orElseThrow().getDisplayName());
        assertEquals("工单", result.stream()
                .filter(item -> "ticket".equals(item.getTypeCode()))
                .findFirst().orElseThrow().getDisplayName());
    }

    @Test
    void tenantOfflineObjectHidesGlobalObject() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "Alice", "TENANT_A", List.of(), List.of(), null, null, null));
        when(businessObjectMapper.selectList(any()))
                .thenReturn(List.of(object("global-expense", "GLOBAL", "expense_report", "报销单", "PUBLISHED", 1)))
                .thenReturn(List.of(object("tenant-expense", "TENANT_A", "expense_report", "报销单", "OFFLINE", 1)));

        assertEquals(0, service.listPublishedForCurrentTenant().size());
    }

    @Test
    void tenantDraftDoesNotOverridePublishedGlobalObject() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "Alice", "TENANT_A", List.of(), List.of(), null, null, null));
        when(businessObjectMapper.selectList(any()))
                .thenReturn(List.of(object("global-expense", "GLOBAL", "expense_report", "报销单", "PUBLISHED", 1)))
                .thenReturn(List.of(object("tenant-expense", "TENANT_A", "expense_report", "租户草稿", "DRAFT", 1)));

        List<BusinessObjectSummaryResponse> result = service.listPublishedForCurrentTenant();

        assertEquals(1, result.size());
        assertEquals("报销单", result.getFirst().getDisplayName());
    }

    private BusinessObject object(String id, String tenantId, String typeCode, String displayName,
                                  String status, int version) {
        BusinessObject object = new BusinessObject();
        object.setId(id);
        object.setTenantId(tenantId);
        object.setTypeCode(typeCode);
        object.setDisplayName(displayName);
        object.setServiceCode(typeCode + "-service");
        object.setStatus(status);
        object.setVersion(version);
        return object;
    }
}
