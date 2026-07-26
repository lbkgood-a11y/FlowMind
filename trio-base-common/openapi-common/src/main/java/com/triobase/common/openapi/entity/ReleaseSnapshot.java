package com.triobase.common.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.openapi.PostgresJsonbTypeHandler;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.enums.VersionLifecycleState;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "oa_release_snapshot", autoResultMap = true)
public class ReleaseSnapshot {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private Environment environment;
    private String routeDefinitionId;
    private String routeVersionId;
    private Integer releaseNumber;
    private VersionLifecycleState lifecycleState;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode pinnedDependencies;
    private String snapshotHash;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode validationResult;
    private String releaseNotes;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime deprecatedAt;
}
