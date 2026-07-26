package com.triobase.common.openapi.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.enums.ExecutionMode;
import com.triobase.common.openapi.enums.VersionLifecycleState;
import com.triobase.common.openapi.entity.VersionedEntity;
import com.triobase.common.openapi.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_route_version", autoResultMap = true)
public class RouteVersion extends VersionedEntity {
    private String routeDefinitionId;
    private Integer versionNumber;
    private Environment environment;
    private VersionLifecycleState lifecycleState;
    private Integer priority;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private Boolean enabled;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode routePredicate;
    private ExecutionMode executionMode;
    private String connectorVersionId;
    private String requestMappingVersionId;
    private String responseMappingVersionId;
    private String orchestrationVersionId;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
