package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.VersionedEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_connector_version", autoResultMap = true)
public class ConnectorVersion extends VersionedEntity {
    private String connectorEndpointId;
    private Integer versionNumber;
    private VersionLifecycleState lifecycleState;
    private String baseUrl;
    private String operationPath;
    private String httpMethod;
    private Integer timeoutMillis;
    private ConnectorOperationClass operationClass;
    private AuthenticationType authenticationType;
    private String secretReference;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode networkPolicy;
    private Long responseSizeLimit;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
