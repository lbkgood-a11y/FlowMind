package com.triobase.service.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StartProcessRequest {
    private String processPackageId;
    private Integer version;
    private String processKey;    // 流程包唯一标识
    private String title;         // 实例标题，可选，默认自动生成
    private Map<String, Object> formData;  // 表单数据
    private String launchMode;    // EXISTING_DOCUMENT / CREATE_AND_LAUNCH
    private String businessType;
    private String businessId;
    private String idempotencyKey;
}
