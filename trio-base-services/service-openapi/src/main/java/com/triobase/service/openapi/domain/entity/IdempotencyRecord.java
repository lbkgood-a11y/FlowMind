package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.Environment;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("oa_idempotency_record")
public class IdempotencyRecord {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private Environment environment;
    private String applicationClientId;
    private String routeDefinitionId;
    private String releaseSnapshotId;
    private String idempotencyKey;
    private String requestHash;
    private String executionId;
    private String recordState;
    private String responseReference;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
