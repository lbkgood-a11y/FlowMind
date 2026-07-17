package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.PolicyScopeType;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.TenantEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_traffic_policy_version", autoResultMap=true)
public class TrafficPolicyVersion extends TenantEntity {
 private Environment environment; private PolicyScopeType scopeType; private String scopeId; private Long policyVersion;
 private VersionLifecycleState lifecycleState;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode policyContent;
 private String contentHash; private String signature; private String publishedBy; private LocalDateTime publishedAt;
}
