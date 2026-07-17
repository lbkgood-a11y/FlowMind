package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.ProductChangeClassification;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.VersionedEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_api_product_version", autoResultMap=true)
public class ApiProductVersion extends VersionedEntity {
 private String apiProductId; private String semanticVersion; private Integer majorVersion; private Integer minorVersion; private Integer patchVersion;
 private VersionLifecycleState lifecycleState; private String documentation; private String terms;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode pinnedRoutes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode pinnedContracts;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode scopes;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode trafficPolicy;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode securityPolicy;
 private ProductChangeClassification changeClassification; private String migrationNotice;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode validationResult;
 private String publishedBy; private LocalDateTime publishedAt;
}
