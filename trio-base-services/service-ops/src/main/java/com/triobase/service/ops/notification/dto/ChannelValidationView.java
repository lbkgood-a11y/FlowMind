package com.triobase.service.ops.notification.dto;

/** 连通性验证只返回能力状态和安全摘要，不包含异常堆栈、地址或凭据元数据。 */
public record ChannelValidationView(String channelCode, String capabilityState,
                                    String adapterKey, String adapterVersion, String safeSummary) {
}

