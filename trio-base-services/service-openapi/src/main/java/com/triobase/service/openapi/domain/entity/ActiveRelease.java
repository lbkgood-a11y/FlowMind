package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.triobase.service.openapi.domain.enums.Environment;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("oa_active_release")
public class ActiveRelease {
    private String routeDefinitionId;
    private Environment environment;
    private String releaseSnapshotId;
    private Long policyVersion;
    private String activatedBy;
    private LocalDateTime activatedAt;
    @Version
    private Long rowVersion;
}
