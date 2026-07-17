package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.entity.BaseEntity;
import com.triobase.service.openapi.domain.enums.ApprovalDecision;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="oa_asset_approval", autoResultMap=true)
public class AssetApproval extends BaseEntity {
 private String tenantId; private String assetType; private String assetId; private Environment environment; private String approvalRole;
 private String submittedBy; private String decidedBy; private ApprovalDecision decision;
 @TableField(typeHandler=PostgresJsonbTypeHandler.class) private JsonNode evidence;
 private LocalDateTime decidedAt;
}
