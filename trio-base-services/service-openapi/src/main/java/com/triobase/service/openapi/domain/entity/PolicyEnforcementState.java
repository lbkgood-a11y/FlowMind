package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.Environment;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("oa_policy_enforcement_state")
public class PolicyEnforcementState {
 private String enforcementPoint; private String tenantId; private Environment environment;
 private Long requiredPolicyVersion; private Long appliedPolicyVersion; private LocalDateTime lastReportedAt; private String driftState;
}
