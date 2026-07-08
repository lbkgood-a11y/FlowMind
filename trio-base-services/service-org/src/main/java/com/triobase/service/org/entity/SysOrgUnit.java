package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_org_unit")
public class SysOrgUnit {
    @TableId
    private String id;
    private String parentId;
    private String unitCode;
    private String unitName;
    private String treePath;
    private Integer sortOrder;
    private Short status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
