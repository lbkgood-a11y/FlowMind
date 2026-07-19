package com.triobase.service.action.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_action_definition_snapshot")
public class ActionDefinitionSnapshot extends BaseEntity {
    private String actionType;
    private String ownerService;
    private String targetType;
    private Integer version;
    private String status;
    private String definitionJson;
    private String schemaHash;
    private LocalDateTime publishedAt;
}
