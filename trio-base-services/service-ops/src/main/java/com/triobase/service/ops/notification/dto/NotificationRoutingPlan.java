package com.triobase.service.ops.notification.dto;

import java.util.List;

/** 路由决策显式记录不可用与静默时段跳过，禁止将“已计划”等同于投递成功。 */
public record NotificationRoutingPlan(String categoryCode, String priorityCode,
                                      boolean mandatoryCategory, List<ChannelDecision> decisions) {
    public record ChannelDecision(String channelCode, String decision, String reason) { }
}

