package com.triobase.service.tenant.dto;

import com.triobase.service.tenant.entity.SysTenantSetting;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantSettingResponse {
    private String id;
    private String tenantId;
    private String settingKey;
    private String settingValue;
    private String valueType;
    private Boolean sensitive;
    private Boolean enabled;
    private String description;
    private LocalDateTime updatedAt;

    public static TenantSettingResponse from(SysTenantSetting setting) {
        TenantSettingResponse response = new TenantSettingResponse();
        response.setId(setting.getId());
        response.setTenantId(setting.getTenantId());
        response.setSettingKey(setting.getSettingKey());
        response.setSettingValue(setting.getSettingValue());
        response.setValueType(setting.getValueType());
        response.setSensitive(setting.getSensitiveFlag() != null && setting.getSensitiveFlag() == 1);
        response.setEnabled(setting.getStatus() == null || setting.getStatus() == 1);
        response.setDescription(setting.getDescription());
        response.setUpdatedAt(setting.getUpdatedAt());
        return response;
    }
}
