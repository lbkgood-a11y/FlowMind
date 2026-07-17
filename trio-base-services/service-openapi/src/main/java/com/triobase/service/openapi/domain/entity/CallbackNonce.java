package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oa_callback_nonce")
public class CallbackNonce {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String applicationClientId;
    private String callbackProfileVersionId;
    private String nonce;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
