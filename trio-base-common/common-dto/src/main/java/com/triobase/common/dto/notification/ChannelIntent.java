package com.triobase.common.dto.notification;

import java.util.List;

/**
 * 通知渠道意图，而非投递成功承诺。
 *
 * <p>运行时只能执行能力状态为 READY 的渠道；未安装适配器的外部渠道必须记录为跳过，
 * 不得伪造成功回执。</p>
 */
public record ChannelIntent(List<Channel> orderedChannels, boolean fallbackEnabled) {
    public enum Channel {
        IN_APP,
        EMAIL,
        SMS,
        WE_COM,
        DINGTALK
    }
}
