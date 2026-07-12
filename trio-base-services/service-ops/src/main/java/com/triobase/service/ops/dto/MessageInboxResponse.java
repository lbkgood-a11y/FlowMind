package com.triobase.service.ops.dto;

import com.triobase.service.ops.entity.OpsMessage;
import com.triobase.service.ops.entity.OpsMessageRecipient;
import lombok.Data;

@Data
public class MessageInboxResponse {
    private OpsMessage message;
    private OpsMessageRecipient recipient;
}
