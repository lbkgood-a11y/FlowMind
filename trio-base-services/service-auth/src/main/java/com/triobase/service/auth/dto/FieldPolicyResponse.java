package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import lombok.Data;

@Data
public class FieldPolicyResponse {
    private String id;
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String resourceCode;
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String effect;
    private Short status;
    private String description;

    public static FieldPolicyResponse from(SysAuthFieldPolicy policy) {
        FieldPolicyResponse response = new FieldPolicyResponse();
        response.setId(policy.getId());
        response.setTenantId(policy.getTenantId());
        response.setSubjectType(policy.getSubjectType());
        response.setSubjectId(policy.getSubjectId());
        response.setResourceCode(policy.getResourceCode());
        response.setFieldKey(policy.getFieldKey());
        response.setReadMode(policy.getReadMode());
        response.setWriteMode(policy.getWriteMode());
        response.setMaskStrategy(policy.getMaskStrategy());
        response.setEffect(policy.getEffect());
        response.setStatus(policy.getStatus());
        response.setDescription(policy.getDescription());
        return response;
    }
}
