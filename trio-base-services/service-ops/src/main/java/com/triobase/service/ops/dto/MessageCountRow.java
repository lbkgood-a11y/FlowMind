package com.triobase.service.ops.dto;

/** 消息管理页的单次分组统计投影。 */
public record MessageCountRow(String messageId, long recipientCount, long readCount) {
}
