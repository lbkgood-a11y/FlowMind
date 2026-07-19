package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_version")
public class SysAuthVersion {
    @TableId
    private String versionKey;
    private Long versionValue;
    private LocalDateTime updatedAt;
}
