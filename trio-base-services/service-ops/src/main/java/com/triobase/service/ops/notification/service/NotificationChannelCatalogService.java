package com.triobase.service.ops.notification.service;

import com.triobase.service.ops.notification.entity.NotificationChannelEntity;
import com.triobase.service.ops.notification.mapper.NotificationChannelMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 维护当前租户的固定渠道能力目录。
 *
 * <p>初始化使用数据库唯一约束保证并发幂等。外部渠道即使显示在目录中也保持
 * NOT_CONNECTED；后续只有适配器验证流程能够推进能力状态。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationChannelCatalogService {

    private static final List<String> EXTERNAL_CHANNELS =
            List.of("EMAIL", "SMS", "WE_COM", "DINGTALK");

    private final NotificationChannelMapper mapper;
    private final RequestContextService contextService;

    @Transactional
    public List<NotificationChannelEntity> listCurrentTenant() {
        String tenantId = contextService.tenantId();
        seed(tenantId, "IN_APP", "READY", 1);
        EXTERNAL_CHANNELS.forEach(channel -> seed(tenantId, channel, "NOT_CONNECTED", 0));
        return mapper.findByTenant(tenantId);
    }

    private void seed(String tenantId, String channel, String state, int enabled) {
        mapper.insertIfAbsent(UUID.randomUUID().toString().replace("-", ""),
                tenantId, channel, state, enabled);
    }
}

