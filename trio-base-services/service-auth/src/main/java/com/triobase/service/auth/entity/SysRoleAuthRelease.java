package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_auth_release")
public class SysRoleAuthRelease {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String roleId;
    private String catalogId;
    private String draftId;
    private String previousReleaseId;
    private Long releaseNumber;
    private Long intentVersion;
    private Long catalogVersion;
    private String validationHash;
    private String intentSnapshot;
    private String compiledSnapshot;
    private String businessSummary;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
