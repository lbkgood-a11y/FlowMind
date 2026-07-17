package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.entity.BaseEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_api_product_route_member", autoResultMap=true)
public class ApiProductRouteMember extends BaseEntity {
 private String apiProductVersionId; private String routeKey; private String releaseSnapshotId;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode operations;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode scopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode canonicalStructureVersionIds;
}
