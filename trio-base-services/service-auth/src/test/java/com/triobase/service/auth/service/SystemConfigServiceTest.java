package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.SystemConfigResponse;
import com.triobase.service.auth.dto.UpdateSystemConfigRequest;
import com.triobase.service.auth.entity.SysSystemConfig;
import com.triobase.service.auth.mapper.SystemConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private SystemConfigService systemConfigService;

    @Test
    void detail_shouldMaskSensitiveValues() {
        SysSystemConfig config = baseConfig();
        config.setSensitive((short) 1);
        config.setConfigValue("raw-secret");
        config.setDefaultValue("default-secret");
        when(systemConfigMapper.selectById("CFG001")).thenReturn(config);

        SystemConfigResponse response = systemConfigService.detail("CFG001");

        assertEquals("******", response.getConfigValue());
        assertEquals("******", response.getDefaultValue());
    }

    @Test
    void update_shouldPreserveSensitiveRawValue_whenMaskedValueSubmitted() {
        SysSystemConfig config = baseConfig();
        config.setSensitive((short) 1);
        config.setConfigValue("raw-secret");
        config.setDefaultValue("default-secret");
        when(systemConfigMapper.selectById("CFG001")).thenReturn(config);

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        request.setConfigValue("******");
        request.setDefaultValue("******");
        request.setDescription("updated");
        request.setStatus(1);

        SystemConfigResponse response = systemConfigService.update("CFG001", request);

        assertEquals("raw-secret", config.getConfigValue());
        assertEquals("default-secret", config.getDefaultValue());
        assertEquals("******", response.getConfigValue());
        assertEquals("updated", config.getDescription());
        verify(systemConfigMapper).updateById(config);
    }

    @Test
    void update_shouldRejectInvalidIntegerValue() {
        SysSystemConfig config = baseConfig();
        config.setConfigType("INTEGER");
        when(systemConfigMapper.selectById("CFG001")).thenReturn(config);

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        request.setConfigType("INTEGER");
        request.setConfigValue("abc");

        BizException ex = assertThrows(BizException.class, () -> systemConfigService.update("CFG001", request));

        assertEquals(40092, ex.getCode());
        verify(systemConfigMapper, never()).updateById(any(SysSystemConfig.class));
    }

    private SysSystemConfig baseConfig() {
        SysSystemConfig config = new SysSystemConfig();
        config.setId("CFG001");
        config.setTenantId("default");
        config.setConfigKey("CFG_TEST");
        config.setConfigGroup("TEST");
        config.setConfigType("STRING");
        config.setConfigValue("value");
        config.setDefaultValue("default");
        config.setSensitive((short) 0);
        config.setSystemFlag((short) 1);
        config.setStatus((short) 1);
        config.setSortOrder(100);
        return config;
    }
}
