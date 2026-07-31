package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.auth.entity.SysRoleAuthAudit;
import com.triobase.service.auth.mapper.RoleAuthAuditMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationAuditServiceTest {

    @Mock private RoleAuthAuditMapper auditMapper;
    @Mock private UserRoleMapper userRoleMapper;
    private RoleAuthorizationAuditService service;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        service = new RoleAuthorizationAuditService(auditMapper, userRoleMapper, new ObjectMapper());
    }

    @Test
    void recordsImpactAndRedactsSecretsFromTechnicalEvidence() {
        when(userRoleMapper.selectCount(any())).thenReturn(4L);

        service.record("tenant-a", "role-1", "draft-1", null, "VALIDATED",
                "用户管理：允许查看", Map.of(
                        "validationToken", "top-secret-token",
                        "nested", Map.of("password", "p@ss", "safe", "kept")));

        ArgumentCaptor<SysRoleAuthAudit> captor = ArgumentCaptor.forClass(SysRoleAuthAudit.class);
        verify(auditMapper).insert(captor.capture());
        SysRoleAuthAudit audit = captor.getValue();
        assertThat(audit.getAffectedUserCount()).isEqualTo(4L);
        assertThat(audit.getTechnicalEvidence()).doesNotContain("top-secret-token", "p@ss");
        assertThat(audit.getTechnicalEvidence()).contains("***", "kept");
    }
}
