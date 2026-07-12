package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {
    private String tenantId;
    private String dictTypeId;
    private String dictCode;
    private String itemLabel;
    private String itemValue;
    private String tagType;
    private String cssClass;
    private Short status;
    private Short systemFlag;
    private Integer sortOrder;
    private String description;
    private String metadata;
}
