package com.triobase.service.ops.dto;

import com.triobase.service.ops.entity.OpsMessage;
import lombok.Data;

@Data
public class MessageAdminResponse {
    private OpsMessage message;
    private long recipientCount;
    private long readCount;
    private long unreadCount;
}
