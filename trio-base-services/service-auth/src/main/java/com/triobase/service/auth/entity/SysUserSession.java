package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_session")
public class SysUserSession extends BaseEntity {
    private String tenantId;
    private String userId;
    private String username;
    private String accessJti;
    private String refreshJti;
    private String sessionStatus;
    private String clientIp;
    private String userAgent;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime logoutAt;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private String traceId;
}
