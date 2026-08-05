package com.triobase.service.ops.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 仅允许经过内部服务认证的调用身份声明自身为生产者。
 *
 * <p>callerService 必须由内部服务认证过滤器提供，Controller 不得从请求正文或普通客户端请求头
 * 直接接收该值。更细粒度的租户委托后续可在此边界接入授权注册表。</p>
 */
@Component
public class DeclaredProducerAuthorizer implements NotificationProducerAuthorizer {
    @Override
    public boolean isAuthorized(String callerService, String tenantId, String declaredProducer) {
        return StringUtils.hasText(tenantId)
                && StringUtils.hasText(callerService)
                && callerService.equals(declaredProducer);
    }
}
