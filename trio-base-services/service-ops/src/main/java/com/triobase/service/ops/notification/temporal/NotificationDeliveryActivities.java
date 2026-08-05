package com.triobase.service.ops.notification.temporal;

import io.temporal.activity.ActivityInterface;

/**
 * 通知投递的 I/O 边界。
 *
 * <p>所有方法都可能被 Temporal 重试；实现必须以租户、任务、游标、收件人和渠道组成的稳定
 * 业务键保证幂等。返回值使用标准 JSON，禁止把数据库实体或 Java 特有序列化写入历史。</p>
 */
@ActivityInterface
public interface NotificationDeliveryActivities {

    String startResolution(String commandJson);

    String resolveAudienceBatch(String batchCommandJson);

    String projectBatch(String projectionCommandJson);

    String completeDelivery(String commandJson);

    String failDelivery(String commandJson);

    String cancelDelivery(String commandJson);

    String withdrawDelivery(String commandJson);
}
