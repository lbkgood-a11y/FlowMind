package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName(value="oa_policy_snapshot",autoResultMap=true)
public class PolicySnapshot {
 @TableId(type=IdType.INPUT) private String id;private String tenantId;private Environment environment;private Long snapshotVersion;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode policyContent;
 private String contentHash;private String signature;private String publishedBy;private LocalDateTime publishedAt;
}
