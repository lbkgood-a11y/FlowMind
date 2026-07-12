package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.SystemConfigResponse;
import com.triobase.service.auth.dto.UpdateSystemConfigRequest;
import com.triobase.service.auth.entity.SysSystemConfig;
import com.triobase.service.auth.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String DEFAULT_TENANT = "default";
    private static final String MASKED_VALUE = "******";

    private final SystemConfigMapper systemConfigMapper;

    public List<SystemConfigResponse> list(String keyword, String configGroup, Integer status) {
        LambdaQueryWrapper<SysSystemConfig> wrapper = new LambdaQueryWrapper<SysSystemConfig>()
                .eq(SysSystemConfig::getTenantId, DEFAULT_TENANT)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(SysSystemConfig::getConfigKey, keyword)
                        .or()
                        .like(SysSystemConfig::getDescription, keyword))
                .eq(StringUtils.hasText(configGroup), SysSystemConfig::getConfigGroup, configGroup)
                .eq(status != null, SysSystemConfig::getStatus, toStatus(status))
                .orderByAsc(SysSystemConfig::getConfigGroup)
                .orderByAsc(SysSystemConfig::getSortOrder)
                .orderByAsc(SysSystemConfig::getConfigKey);
        return systemConfigMapper.selectList(wrapper).stream()
                .map(SystemConfigResponse::from)
                .toList();
    }

    public SystemConfigResponse detail(String id) {
        return SystemConfigResponse.from(requireConfig(id));
    }

    @Transactional
    public SystemConfigResponse update(String id, UpdateSystemConfigRequest request) {
        SysSystemConfig config = requireConfig(id);
        if (request == null) {
            throw new BizException(40091, "SYSTEM_CONFIG_REQUIRED");
        }
        String nextType = StringUtils.hasText(request.getConfigType()) ? request.getConfigType().trim().toUpperCase() : config.getConfigType();
        String nextValue = resolveSubmittedValue(config.getConfigValue(), request.getConfigValue(), config.getSensitive());
        validateValue(nextType, nextValue);
        config.setConfigValue(nextValue);
        config.setDefaultValue(resolveSubmittedValue(config.getDefaultValue(), request.getDefaultValue(), config.getSensitive()));
        config.setConfigType(nextType);
        config.setConfigGroup(StringUtils.hasText(request.getConfigGroup()) ? request.getConfigGroup().trim() : config.getConfigGroup());
        config.setSensitive(toShort(request.getSensitive(), config.getSensitive()));
        config.setStatus(toStatus(request.getStatus()));
        config.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : config.getSortOrder());
        config.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        systemConfigMapper.updateById(config);
        return SystemConfigResponse.from(config);
    }

    public String runtimeValue(String configKey) {
        SysSystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SysSystemConfig>()
                .eq(SysSystemConfig::getTenantId, DEFAULT_TENANT)
                .eq(SysSystemConfig::getConfigKey, configKey)
                .eq(SysSystemConfig::getStatus, (short) 1)
                .last("LIMIT 1"));
        if (config == null) {
            return null;
        }
        return StringUtils.hasText(config.getConfigValue()) ? config.getConfigValue() : config.getDefaultValue();
    }

    private SysSystemConfig requireConfig(String id) {
        SysSystemConfig config = systemConfigMapper.selectById(id);
        if (config == null) {
            throw new BizException(40491, "SYSTEM_CONFIG_NOT_FOUND");
        }
        return config;
    }

    private String resolveSubmittedValue(String currentValue, String submittedValue, Short sensitive) {
        if (sensitive != null && sensitive == 1 && MASKED_VALUE.equals(submittedValue)) {
            return currentValue;
        }
        return submittedValue;
    }

    private void validateValue(String type, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            switch (type) {
                case "INTEGER" -> Integer.parseInt(value);
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException();
                    }
                }
                case "STRING", "JSON" -> {
                    return;
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BizException(40092, "SYSTEM_CONFIG_VALUE_INVALID");
        }
    }

    private Short toStatus(Integer value) {
        return (short) (value != null && value == 0 ? 0 : 1);
    }

    private Short toShort(Integer value, Short currentValue) {
        if (value == null) {
            return currentValue != null ? currentValue : 0;
        }
        return (short) (value == 1 ? 1 : 0);
    }
}
