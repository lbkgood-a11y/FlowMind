package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.model.TenantEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_application_client", autoResultMap=true)
public class ApplicationClient extends TenantEntity {
 private String applicationId; private Environment environment; private String clientKey; private ApplicationLifecycleState lifecycleState;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode networkPolicy;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode securityPolicy;
 private LocalDateTime expiresAt; private String suspensionReason; private Integer securityViolationCount;
}
