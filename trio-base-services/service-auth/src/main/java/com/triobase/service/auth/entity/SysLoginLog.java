package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {
    private String tenantId;
    private String userId;
    private String username;
    private String loginResult;
    private String failureReason;
    private String clientIp;
    private String userAgent;
    private String traceId;
    private LocalDateTime loginAt;
}
