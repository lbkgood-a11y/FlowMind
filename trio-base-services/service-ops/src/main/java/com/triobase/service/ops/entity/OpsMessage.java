package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_message")
public class OpsMessage extends BaseEntity {
    private String tenantId;
    private String title;
    private String content;
    private String messageType;
    private String sourceType;
    private String sourceId;
    private String senderId;
    private String senderName;
}
