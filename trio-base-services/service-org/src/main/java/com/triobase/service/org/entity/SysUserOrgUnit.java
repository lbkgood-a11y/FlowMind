package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_org_unit")
public class SysUserOrgUnit {
    @TableId
    private String id;
    private String userId;
    private String orgUnitId;
    private LocalDateTime createdAt;
}
