package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.entity.BaseEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_subscription_route_override", autoResultMap=true)
public class SubscriptionRouteOverride extends BaseEntity {
 private String subscriptionId; private String routeKey; private Boolean excluded;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode allowedOperations;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode requiredScopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode quotaOverride;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode sourceNetworks;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode structureVersionIds;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode fieldRestrictions;
}
