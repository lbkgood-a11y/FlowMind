package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("oa_application_contact")
public class ApplicationContact extends BaseEntity { private String applicationId; private String contactRole; private String contactName; private String email; private String phone; }
