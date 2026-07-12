package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_file")
public class OpsFile extends BaseEntity {
    private String tenantId;
    private String originalName;
    private String storageName;
    private String contentType;
    private String extension;
    private Long fileSize;
    private String storagePath;
    private String checksum;
    private String ownerUserId;
    private Short status;
    private Short deleted;
    private Long downloadCount;
    private LocalDateTime lastDownloadAt;
}
