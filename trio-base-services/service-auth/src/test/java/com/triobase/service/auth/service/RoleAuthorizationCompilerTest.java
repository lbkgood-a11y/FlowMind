package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.RoleAuthorizationCompilationPlan;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationCompilerTest {

    @Mock private RoleAuthDraftMapper draftMapper;
    @Mock private RoleAuthIntentMapper intentMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private AuthPageCapabilityTargetMapper targetMapper;
    @Mock private AuthPageCatalogMapper catalogMapper;
    @Mock private AuthFieldMapper fieldMapper;
    @Mock private AuthorizationRegistryService registryService;

    private RoleAuthorizationCompiler compiler;
    private SysAuthPageCapability access;
    private SysAuthPageCapability operation;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        compiler = new RoleAuthorizationCompiler(draftMapper, intentMapper, capabilityMapper,
                targetMapper, catalogMapper, fieldMapper, registryService, new ObjectMapper());
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");

        SysRoleAuthDraft draft = new SysRoleAuthDraft();
        draft.setId("draft-1");
        draft.setTenantId("tenant-a");
        draft.setRoleId("role-1");
        draft.setCatalogId("catalog-1");
        draft.setIntentVersion(3L);
        draft.setValidationSummary("用户管理：进入页面、创建用户");
        when(draftMapper.selectOne(any())).thenReturn(draft);

        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("catalog-1");
        catalog.setTenantId("tenant-a");
        catalog.setCatalogVersion(7L);
        catalog.setLifecycleStatus("ACTIVE");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);

        access = capability("cap-access", "USER_ACCESS", "进入用户管理", "ACCESS");
        operation = capability("cap-create", "USER_CREATE", "创建用户", "OPERATION");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(operation, access));
        lenient().when(targetMapper.selectList(any())).thenReturn(List.of(
                target("cap-create", "PAGE_USER_MANAGEMENT", "CREATE"),
                target("cap-access", "PAGE_USER_MANAGEMENT", "ACCESS")));
    }

    @Test
    void compilesDeterministicallyAndNormalizesOperationScope() {
        SysRoleAuthIntent createIntent = intent("cap-create");
        createIntent.setOperationScopeType("assigned_orgs");
        createIntent.setOperationScopeIds("[\"org-b\",\"org-a\"]");
        SysRoleAuthIntent accessIntent = intent("cap-access");
        when(intentMapper.selectList(any())).thenReturn(List.of(createIntent, accessIntent));

        RoleAuthorizationCompilationPlan first = compiler.compile("tenant-a", "draft-1");
        when(intentMapper.selectList(any())).thenReturn(List.of(accessIntent, createIntent));
        RoleAuthorizationCompilationPlan second = compiler.compile("tenant-a", "draft-1");

        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        assertThat(first.getGrants()).extracting(RoleAuthorizationCompilationPlan.GrantProjection::getActionCode)
                .containsExactly("ACCESS", "CREATE");
        assertThat(first.getDataPolicies()).singleElement().satisfies(policy -> {
            assertThat(policy.getScopeType()).isEqualTo("ASSIGNED_ORGS");
            assertThat(policy.getOrganizationIds()).containsExactly("org-b", "org-a");
        });
    }

    @Test
    void failsClosedWhenSelectedCapabilityIsNotReady() {
        operation.setReadinessStatus("PARTIAL");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(operation));
        when(intentMapper.selectList(any())).thenReturn(List.of(intent("cap-create")));

        assertThatThrownBy(() -> compiler.compile("tenant-a", "draft-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("创建用户");
    }

    @Test
    void failsClosedWhenStaleIntentContainsUnsupportedScope() {
        operation.setScopeSupported((short) 0);
        when(capabilityMapper.selectList(any())).thenReturn(List.of(operation));
        SysRoleAuthIntent createIntent = intent("cap-create");
        createIntent.setOperationScopeType("ALL");
        when(intentMapper.selectList(any())).thenReturn(List.of(createIntent));

        assertThatThrownBy(() -> compiler.compile("tenant-a", "draft-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持单独配置数据范围");
    }

    @Test
    void failsClosedWhenAssignedOrganizationScopeHasNoOrganization() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of(operation));
        SysRoleAuthIntent createIntent = intent("cap-create");
        createIntent.setOperationScopeType("ASSIGNED_ORGS");
        createIntent.setOperationScopeIds("[]");
        when(intentMapper.selectList(any())).thenReturn(List.of(createIntent));

        assertThatThrownBy(() -> compiler.compile("tenant-a", "draft-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少需要选择一个组织");
    }

    private SysAuthPageCapability capability(String id, String code, String name, String category) {
        SysAuthPageCapability item = new SysAuthPageCapability();
        item.setId(id);
        item.setTenantId("tenant-a");
        item.setCatalogId("catalog-1");
        item.setCapabilityCode(code);
        item.setCapabilityName(name);
        item.setCapabilityCategory(category);
        item.setReadinessStatus("READY");
        item.setScopeSupported((short) 1);
        item.setFieldPolicySupported((short) 1);
        item.setStatus((short) 1);
        return item;
    }

    private SysAuthPageCapabilityTarget target(String capabilityId, String resource, String action) {
        SysAuthPageCapabilityTarget target = new SysAuthPageCapabilityTarget();
        target.setCapabilityId(capabilityId);
        target.setTenantId("tenant-a");
        target.setResourceCode(resource);
        target.setActionCode(action);
        target.setTargetKind("GRANT");
        target.setStatus((short) 1);
        return target;
    }

    private SysRoleAuthIntent intent(String capabilityId) {
        SysRoleAuthIntent intent = new SysRoleAuthIntent();
        intent.setTenantId("tenant-a");
        intent.setDraftId("draft-1");
        intent.setCapabilityId(capabilityId);
        return intent;
    }
}
