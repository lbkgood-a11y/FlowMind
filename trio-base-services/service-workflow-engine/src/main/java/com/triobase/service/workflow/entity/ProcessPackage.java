package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_package")
public class ProcessPackage extends BaseEntity {
    private String processKey;
    private String name;
    private String category;       // approval / business / integration
    private String description;
    private Integer version;
    private String status;         // DRAFT / PUBLISHED / OFFLINE
    private String processJson;    // 完整的流程包 JSON
    private String formSchema;     // 抽取的表单 JSON Schema
    private String formUiSchema;   // 抽取的 UI Schema
    private String formDefinitionId;
    private Integer formDefinitionVersion;
    private String businessBindingSnapshot;
    private String launchPlanJson;
    private String permissionPlanJson;
    private String closurePlanJson;
    private String agentFollowUpPlanJson;
    private String sourcePackageId;
    private LocalDateTime publishedAt;
}
