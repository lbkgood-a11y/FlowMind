package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.SubscriptionLifecycleState;
import com.triobase.service.openapi.domain.model.TenantEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_product_subscription", autoResultMap=true)
public class ProductSubscription extends TenantEntity {
 private String applicationClientId; private String apiProductVersionId; private Environment environment;
 private SubscriptionLifecycleState lifecycleState;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode requestedScopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode effectiveScopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode overrides;
 private LocalDateTime effectiveFrom; private LocalDateTime effectiveUntil; private String requestedBy;
 private LocalDateTime activatedAt; private String suspensionReason;
}
