package com.triobase.service.ops.notification.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按渠道代码发现唯一适配器；重复注册在启动时失败，避免运行期不确定路由。 */
@Component
public class NotificationChannelAdapterRegistry {
    private final Map<String, NotificationChannelAdapter> adapters;

    public NotificationChannelAdapterRegistry(List<NotificationChannelAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                NotificationChannelAdapter::channelCode, Function.identity()));
    }

    public NotificationChannelAdapter find(String channelCode) {
        return adapters.get(channelCode);
    }
}

