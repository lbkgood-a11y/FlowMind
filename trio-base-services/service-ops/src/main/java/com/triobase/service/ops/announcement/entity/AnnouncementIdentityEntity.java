package com.triobase.service.ops.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_announcement_identity")
public class AnnouncementIdentityEntity extends BaseEntity {
    private String tenantId;
    private String announcementCode;
    private String currentVersionId;
    private String legacyId;
}
