package com.triobase.service.tenant.dto;

import lombok.Data;

@Data
public class SaveTenantSettingRequest {
    private String settingKey;
    private String settingValue;
    private String valueType;
    private Boolean sensitive;
    private Boolean enabled;
    private String description;
}
