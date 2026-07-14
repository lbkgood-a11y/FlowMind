package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_agent_action")
public class BusinessObjectAgentAction extends BaseEntity {
    private String objectId;
    private String agentActionCode;
    private String displayName;
    private String executorKey;
    private String permissionAction;
    private String paramSchemaJson;
    private String resultSchemaJson;
    private String modeDefault;
    private Integer sortOrder;
    private String metadataJson;
}
