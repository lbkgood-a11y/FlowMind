package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_page_capability_dependency")
public class SysAuthPageCapabilityDependency {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String capabilityId;
    private String requiredCapabilityId;
    private String createdBy;
    private LocalDateTime createdAt;
}
