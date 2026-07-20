package com.triobase.service.tenant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.tenant.dto.CreateTenantRequest;
import com.triobase.service.tenant.dto.SaveTenantSettingRequest;
import com.triobase.service.tenant.dto.TenantValidationResponse;
import com.triobase.service.tenant.dto.UpdateTenantStatusRequest;
import com.triobase.service.tenant.entity.SysTenant;
import com.triobase.service.tenant.entity.SysTenantSetting;
import com.triobase.service.tenant.mapper.TenantMapper;
import com.triobase.service.tenant.mapper.TenantSettingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private TenantSettingMapper settingMapper;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantMapper, settingMapper, new ObjectMapper());
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createTenant_shouldRejectDuplicateTenantId() {
        CreateTenantRequest request = createTenantRequest("tenant-a");
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> tenantService.createTenant(request));

        assertEquals(40063, ex.getCode());
    }

    @Test
    void createTenant_shouldNormalizeTenantIdAndDefaults() {
        CreateTenantRequest request = createTenantRequest("Tenant-A");
        when(tenantMapper.selectCount(any())).thenReturn(0L);

        tenantService.createTenant(request);

        ArgumentCaptor<SysTenant> captor = ArgumentCaptor.forClass(SysTenant.class);
        verify(tenantMapper).insert(captor.capture());
        SysTenant tenant = captor.getValue();
        assertEquals("tenant-a", tenant.getId());
        assertEquals("tenant-a", tenant.getTenantCode());
        assertEquals("ACTIVE", tenant.getStatus());
        assertEquals("SHARED_SCHEMA", tenant.getIsolationMode());
        assertEquals("BASIC", tenant.getPlanCode());
    }

    @Test
    void updateStatus_shouldRejectDisablingDefaultTenant() {
        setAdminContext();
        SysTenant tenant = tenant("default", "ACTIVE");
        when(tenantMapper.selectById("default")).thenReturn(tenant);

        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest();
        request.setStatus("SUSPENDED");

        BizException ex = assertThrows(BizException.class, () -> tenantService.updateStatus("default", request));

        assertEquals(40065, ex.getCode());
    }

    @Test
    void validateTenant_shouldRejectExpiredTenant() {
        SysTenant tenant = tenant("tenant-a", "ACTIVE");
        tenant.setExpireAt(LocalDateTime.now().minusDays(1));
        when(tenantMapper.selectById("tenant-a")).thenReturn(tenant);

        TenantValidationResponse response = tenantService.validateTenant("tenant-a");

        assertFalse(response.isActive());
        assertEquals("TENANT_EXPIRED", response.getInactiveReason());
    }

    @Test
    void saveSetting_shouldRejectInvalidJsonValue() {
        setAdminContext();
        when(tenantMapper.selectById("tenant-a")).thenReturn(tenant("tenant-a", "ACTIVE"));

        SaveTenantSettingRequest request = new SaveTenantSettingRequest();
        request.setValueType("JSON");
        request.setSettingValue("{bad json");

        BizException ex = assertThrows(BizException.class,
                () -> tenantService.saveSetting("tenant-a", "feature.config", request));

        assertEquals(40074, ex.getCode());
    }

    @Test
    void saveSetting_shouldUpdateExistingSetting() {
        setAdminContext();
        when(tenantMapper.selectById("tenant-a")).thenReturn(tenant("tenant-a", "ACTIVE"));
        SysTenantSetting existing = new SysTenantSetting();
        existing.setId("SETTING1");
        existing.setTenantId("tenant-a");
        existing.setSettingKey("feature.enabled");
        when(settingMapper.selectOne(any())).thenReturn(existing);

        SaveTenantSettingRequest request = new SaveTenantSettingRequest();
        request.setValueType("BOOLEAN");
        request.setSettingValue("true");

        tenantService.saveSetting("tenant-a", "feature.enabled", request);

        verify(settingMapper).updateById(existing);
        assertEquals((short) 1, existing.getStatus());
        assertEquals((short) 0, existing.getSensitiveFlag());
    }

    private CreateTenantRequest createTenantRequest(String tenantId) {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setTenantId(tenantId);
        request.setTenantName("租户 A");
        return request;
    }

    private SysTenant tenant(String tenantId, String status) {
        SysTenant tenant = new SysTenant();
        tenant.setId(tenantId);
        tenant.setTenantCode(tenantId);
        tenant.setTenantName("Tenant " + tenantId);
        tenant.setStatus(status);
        tenant.setIsolationMode("SHARED_SCHEMA");
        tenant.setPlanCode("BASIC");
        return tenant;
    }

    private void setAdminContext() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001",
                "admin",
                "default",
                List.of("ADMIN"),
                List.of("*"),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null));
    }
}
