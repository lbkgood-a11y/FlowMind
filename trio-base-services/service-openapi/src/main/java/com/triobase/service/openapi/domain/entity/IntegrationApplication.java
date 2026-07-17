package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import com.triobase.service.openapi.domain.model.TenantEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_application", autoResultMap=true)
public class IntegrationApplication extends TenantEntity {
 private String applicationKey; private String displayName; private String ownerId; private String purpose; private RiskLevel riskLevel;
 private ApplicationLifecycleState lifecycleState;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode approvalEvidence;
 private String suspensionReason;
}
