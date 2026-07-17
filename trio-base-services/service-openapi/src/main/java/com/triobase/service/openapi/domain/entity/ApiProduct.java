package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.ProductVisibility;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import com.triobase.service.openapi.domain.model.TenantEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName(value="oa_api_product", autoResultMap=true)
public class ApiProduct extends TenantEntity {
 private String productKey; private String displayName; private String ownerId; private String audience;
 private RiskLevel riskLevel; private ProductVisibility visibility; private String documentation; private String terms;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode defaultScopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode defaultTrafficPolicy;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode defaultSecurityPolicy;
 private AssetLifecycleState lifecycleState;
}
