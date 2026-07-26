package com.triobase.common.openapi.resolution;

import java.time.LocalDateTime;

/**
 * Checks whether an application has runtime access to a specific route.
 * Implemented by the management service; consumed by the runtime service
 * to enforce subscription-based access control without depending on
 * {@code ProductSubscriptionService} directly.
 */
@FunctionalInterface
public interface SubscriptionAccessChecker {

    /**
     * @throws com.triobase.common.core.exception.BizException if access is denied
     */
    void requireRuntimeAccess(String applicationClientId, String subscriptionId,
                              String routeKey, String operation, LocalDateTime at);
}
