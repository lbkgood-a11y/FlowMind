package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("oa_application_owner")
public class ApplicationOwner extends BaseEntity { private String applicationId; private String ownerId; private String ownerRole; }
