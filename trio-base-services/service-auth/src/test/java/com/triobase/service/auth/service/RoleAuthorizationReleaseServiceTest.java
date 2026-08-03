package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.PublishRoleAuthorizationRequest;
import com.triobase.service.auth.dto.RoleAuthorizationCompilationPlan;
import com.triobase.service.auth.dto.RoleAuthorizationValidationResponse;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthRelease;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.DataPolicyDimensionMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import com.triobase.service.auth.mapper.RoleAuthReleaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationReleaseServiceTest {

    @Mock private RoleAuthorizationCompiler compiler;
    @Mock private RoleAuthDraftMapper draftMapper;
    @Mock private RoleAuthIntentMapper intentMapper;
    @Mock private RoleAuthReleaseMapper releaseMapper;
    @Mock private RoleAuthActiveReleaseMapper activeReleaseMapper;
    @Mock private RoleAuthCompiledEvidenceMapper evidenceMapper;
    @Mock private AuthGrantMapper grantMapper;
    @Mock private DataPolicyMapper dataPolicyMapper;
    @Mock private DataPolicyDimensionMapper dimensionMapper;
    @Mock private AuthFieldPolicyMapper fieldPolicyMapper;
    @Mock private AuthorizationRegistryService registryService;
    @Mock private AuthorizationVersionService versionService;
    @Mock private RoleAuthorizationAuditService auditService;
    @Mock private AuthorizationManagementModeService managementModeService;

    private RoleAuthorizationReleaseService service;
    private SysRoleAuthDraft draft;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        service = new RoleAuthorizationReleaseService(compiler, draftMapper, intentMapper,
                releaseMapper, activeReleaseMapper, evidenceMapper, grantMapper, dataPolicyMapper,
                dimensionMapper, fieldPolicyMapper, registryService, versionService, auditService,
                managementModeService, new ObjectMapper());
        draft = new SysRoleAuthDraft();
        draft.setId("draft-1");
        draft.setTenantId("tenant-a");
        draft.setRoleId("role-1");
        draft.setCatalogId("catalog-1");
        draft.setIntentVersion(2L);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        lenient().when(draftMapper.selectOne(any())).thenReturn(draft);
    }

    @Test
    void validationTokenBecomesStaleWhenActorAuthorityChanges() {
        RoleAuthorizationCompilationPlan plan = new RoleAuthorizationCompilationPlan();
        plan.setTenantId("tenant-a");
        plan.setRoleId("role-1");
        plan.setDraftId("draft-1");
        plan.setCatalogId("catalog-1");
        plan.setCatalogVersion(9L);
        plan.setIntentVersion(2L);
        plan.setBusinessSummary("用户管理：只允许查看");
        when(compiler.compile("tenant-a", "draft-1")).thenReturn(plan);
        when(registryService.currentGrantVersion()).thenReturn(7L, 8L);

        RoleAuthorizationValidationResponse validation = service.validate("tenant-a", "draft-1", 2L);
        assertThat(validation.getValidationToken()).isNotBlank();
        assertThat(draft.getValidationAuthorityVersion()).isEqualTo(7L);

        PublishRoleAuthorizationRequest request = new PublishRoleAuthorizationRequest();
        request.setTenantId("tenant-a");
        request.setExpectedVersion(2L);
        request.setValidationToken(validation.getValidationToken());
        assertThatThrownBy(() -> service.publish("draft-1", request))
                .isInstanceOf(BizException.class)
                .hasMessage("ROLE_AUTH_VALIDATION_STALE");
        verify(releaseMapper, never()).insert(any(SysRoleAuthRelease.class));
    }

    @Test
    void validationRecordsExplicitMigrationExpansionReview() {
        draft.setMigrationExpansionDetected((short) 1);
        RoleAuthorizationCompilationPlan plan = new RoleAuthorizationCompilationPlan();
        plan.setTenantId("tenant-a"); plan.setRoleId("role-1"); plan.setDraftId("draft-1");
        plan.setCatalogId("catalog-1"); plan.setCatalogVersion(1L); plan.setIntentVersion(2L);
        plan.setBusinessSummary("已复核依赖功能");
        when(compiler.compile("tenant-a", "draft-1")).thenReturn(plan);

        service.validate("tenant-a", "draft-1", 2L, true);

        assertThat(draft.getMigrationExpansionAcknowledged()).isEqualTo((short) 1);
        verify(draftMapper).updateById(draft);
    }

    @Test
    void publishRejectsDraftThatWasNeverValidated() {
        draft.setDraftStatus("DRAFT");
        PublishRoleAuthorizationRequest request = new PublishRoleAuthorizationRequest();
        request.setTenantId("tenant-a");
        request.setExpectedVersion(2L);
        request.setValidationToken("not-a-validation-token");

        assertThatThrownBy(() -> service.publish("draft-1", request))
                .isInstanceOf(BizException.class)
                .hasMessage("ROLE_AUTH_VALIDATION_STALE");
        verify(releaseMapper, never()).insert(any(SysRoleAuthRelease.class));
    }

    @Test
    void rollbackReplaysImmutableSnapshotWithoutRewritingEvidence() throws Exception {
        RoleAuthorizationCompilationPlan plan = new RoleAuthorizationCompilationPlan();
        plan.setTenantId("tenant-a");
        plan.setRoleId("role-1");
        plan.setDraftId("draft-old");
        plan.setCatalogId("catalog-1");
        plan.setCatalogVersion(3L);
        plan.setIntentVersion(1L);
        plan.setBusinessSummary("用户管理：只允许进入页面");
        SysRoleAuthRelease release = new SysRoleAuthRelease();
        release.setId("release-1");
        release.setTenantId("tenant-a");
        release.setRoleId("role-1");
        release.setReleaseNumber(1L);
        release.setCompiledSnapshot(new ObjectMapper().writeValueAsString(plan));
        when(releaseMapper.selectOne(any())).thenReturn(release);

        service.rollback("tenant-a", "role-1", "release-1");

        verify(evidenceMapper, never()).insert(any(SysRoleAuthCompiledEvidence.class));
        verify(activeReleaseMapper).insert(any(SysRoleAuthActiveRelease.class));
        ArgumentCaptor<LambdaQueryWrapper<com.triobase.service.auth.entity.SysAuthGrant>> grantDelete =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(grantMapper).delete(grantDelete.capture());
        assertThat(grantDelete.getValue().getSqlSegment()).contains("description LIKE");
        ArgumentCaptor<LambdaQueryWrapper<SysDataPolicy>> policyDelete = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(dataPolicyMapper).delete(policyDelete.capture());
        assertThat(policyDelete.getValue().getSqlSegment())
                .contains("description LIKE");
        ArgumentCaptor<LambdaQueryWrapper<com.triobase.service.auth.entity.SysAuthFieldPolicy>> fieldDelete =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(fieldPolicyMapper).delete(fieldDelete.capture());
        assertThat(fieldDelete.getValue().getSqlSegment()).contains("description LIKE");
    }
}
