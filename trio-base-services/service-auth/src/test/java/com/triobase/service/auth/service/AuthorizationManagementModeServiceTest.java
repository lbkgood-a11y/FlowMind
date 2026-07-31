package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.AuthorizationCompatibilityDashboardResponse;
import com.triobase.service.auth.entity.SysAuthTenantManagementMode;
import com.triobase.service.auth.mapper.AuthTenantManagementModeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationManagementModeServiceTest {

    @Mock private AuthTenantManagementModeMapper modeMapper;
    @Mock private AuthorizationRegistryService registryService;
    @Mock private AuthorizationCompatibilityService compatibilityService;

    @Test
    void pageCapabilityCutoverIsBlockedUntilProductionAcceptancePasses() {
        AuthorizationManagementModeService service = new AuthorizationManagementModeService(
                modeMapper, registryService, compatibilityService);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        when(compatibilityService.assess("tenant-a")).thenReturn(
                AuthorizationCompatibilityDashboardResponse.builder()
                        .tenantId("tenant-a").cutoverReady(false)
                        .blockers(List.of("有 1 个启用角色尚未发布页面功能授权"))
                        .roleStatuses(List.of()).build());

        assertThatThrownBy(() -> service.update("tenant-a", "PAGE_CAPABILITY"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生产验收")
                .hasMessageContaining("尚未发布");
        verify(modeMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysAuthTenantManagementMode.class));
        verify(modeMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SysAuthTenantManagementMode.class));
    }

    @Test
    void acceptedCutoverPersistsPageCapabilityMode() {
        AuthorizationManagementModeService service = new AuthorizationManagementModeService(
                modeMapper, registryService, compatibilityService);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        when(compatibilityService.assess("tenant-a")).thenReturn(
                AuthorizationCompatibilityDashboardResponse.builder()
                        .tenantId("tenant-a").cutoverReady(true)
                        .blockers(List.of()).roleStatuses(List.of()).build());

        var result = service.update("tenant-a", "PAGE_CAPABILITY");

        assertThat(result.getManagementMode()).isEqualTo("PAGE_CAPABILITY");
        verify(modeMapper).insert(any(SysAuthTenantManagementMode.class));
    }

    @Test
    void completedCutoverCannotReopenLegacyWrites() {
        AuthorizationManagementModeService service = new AuthorizationManagementModeService(
                modeMapper, registryService, compatibilityService);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        SysAuthTenantManagementMode current = new SysAuthTenantManagementMode();
        current.setTenantId("tenant-a");
        current.setManagementMode("PAGE_CAPABILITY");
        when(modeMapper.selectById("tenant-a")).thenReturn(current);

        assertThatThrownBy(() -> service.update("tenant-a", "MIGRATION"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能重新开启旧授权写入");
    }
}
