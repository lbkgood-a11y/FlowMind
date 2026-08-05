package com.triobase.service.ops.notification.temporal;

import com.triobase.common.temporal.policy.RetryPolicyPresets;
import com.triobase.service.ops.notification.service.NotificationDeliveryActivityService;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Temporal Activity 适配器。
 *
 * <p>副作用由事务服务使用稳定业务键去重；{@link RetryPolicyPresets} 在 Workflow Stub 上显式
 * 配置，重试次数本身不作为幂等机制。</p>
 */
@Component
@ActivityImpl(taskQueues = "service-ops")
@RequiredArgsConstructor
public class NotificationDeliveryActivitiesImpl implements NotificationDeliveryActivities {

    private final NotificationDeliveryActivityService service;

    @Override
    public String startResolution(String commandJson) {
        return service.startResolution(commandJson);
    }

    @Override
    public String resolveAudienceBatch(String batchCommandJson) {
        return service.resolveAudienceBatch(batchCommandJson);
    }

    @Override
    public String projectBatch(String projectionCommandJson) {
        return service.projectBatch(projectionCommandJson);
    }

    @Override
    public String completeDelivery(String commandJson) {
        return service.completeDelivery(commandJson);
    }

    @Override
    public String failDelivery(String commandJson) {
        return service.failDelivery(commandJson);
    }

    @Override
    public String cancelDelivery(String commandJson) {
        return service.cancelDelivery(commandJson);
    }

    @Override
    public String withdrawDelivery(String commandJson) {
        return service.withdrawDelivery(commandJson);
    }
}
