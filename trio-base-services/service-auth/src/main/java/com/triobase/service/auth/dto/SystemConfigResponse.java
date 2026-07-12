package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysSystemConfig;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Data
public class SystemConfigResponse {
    private String id;
    private String configKey;
    private String configValue;
    private String defaultValue;
    private String configType;
    private String configGroup;
    private Integer sensitive;
    private Integer systemFlag;
    private Integer status;
    private Integer sortOrder;
    private String description;
    private LocalDateTime updatedAt;

    public static SystemConfigResponse from(SysSystemConfig config) {
        SystemConfigResponse response = new SystemConfigResponse();
        response.setId(config.getId());
        response.setConfigKey(config.getConfigKey());
        response.setConfigValue(maskIfSensitive(config.getConfigValue(), config.getSensitive()));
        response.setDefaultValue(maskIfSensitive(config.getDefaultValue(), config.getSensitive()));
        response.setConfigType(config.getConfigType());
        response.setConfigGroup(config.getConfigGroup());
        response.setSensitive(config.getSensitive() != null ? config.getSensitive().intValue() : 0);
        response.setSystemFlag(config.getSystemFlag() != null ? config.getSystemFlag().intValue() : 0);
        response.setStatus(config.getStatus() != null ? config.getStatus().intValue() : 1);
        response.setSortOrder(config.getSortOrder());
        response.setDescription(config.getDescription());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }

    private static String maskIfSensitive(String value, Short sensitive) {
        if (sensitive == null || sensitive == 0 || !StringUtils.hasText(value)) {
            return value;
        }
        return "******";
    }
}
