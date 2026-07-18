package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.OpenApiStructure;
import com.triobase.service.openapi.domain.entity.IntegrationApplication;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.infrastructure.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenApiLifecycleCatalogServiceTest {
    @Mock OpenApiStructureMapper structures; @Mock StructureVersionMapper structureVersions;
    @Mock MappingSetMapper mappings; @Mock MappingVersionMapper mappingVersions;
    @Mock ValueMapSetMapper valueMaps; @Mock ValueMapVersionMapper valueMapVersions;
    @Mock ConnectorEndpointMapper connectors; @Mock ConnectorVersionMapper connectorVersions;
    @Mock RouteDefinitionMapper routes; @Mock RouteVersionMapper routeVersions;
    @Mock ReleaseSnapshotMapper releases;
    @Mock OrchestrationDefinitionMapper orchestrations; @Mock OrchestrationVersionMapper orchestrationVersions;
    @Mock CallbackProfileMapper callbacks; @Mock CallbackProfileVersionMapper callbackVersions;
    @Mock ApiProductMapper products; @Mock ApiProductVersionMapper productVersions;
    @Mock IntegrationApplicationMapper applications; @Mock ApplicationClientMapper applicationClients;
    @Mock ProductSubscriptionMapper subscriptions; @Mock AssetApprovalMapper approvals;
    @Mock TrafficPolicyVersionMapper policies; @Mock PolicySnapshotMapper snapshots;

    @Test
    void rejectsAssetTypesOutsideTheFixedAllowList() {
        assertThatThrownBy(() -> service().search("database-table", null, null, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_LIFECYCLE_ASSET_TYPE_UNSUPPORTED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPagedRedactedApplicationSummaries() {
        IntegrationApplication application = new IntegrationApplication();
        application.setId("app-1");
        application.setTenantId("tenant-a");
        application.setApplicationKey("erp");
        application.setDisplayName("ERP");
        application.setLifecycleState(ApplicationLifecycleState.ACTIVE);
        application.setApprovalEvidence(new ObjectMapper().createObjectNode().put("token", "secret"));
        when(applications.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<IntegrationApplication> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(application)); page.setTotal(1); return page;
        });

        var result = service().search("applications", "erp", "ACTIVE", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().getFirst().assetKey()).isEqualTo("erp");
        assertThat(result.getRecords().getFirst().detail().path("approvalEvidence").asText())
                .isEqualTo("***REDACTED***");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsReadableReferenceCodesForVersionAssets() {
        OpenApiStructure structure = new OpenApiStructure();
        structure.setId("structure-main-1");
        structure.setStructureKey("order");
        structure.setDisplayName("Order");
        StructureVersion version = new StructureVersion();
        version.setId("structure-version-1");
        version.setStructureId("structure-main-1");
        version.setVersionNumber(2);
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        when(structures.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(structure));
        when(structureVersions.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<StructureVersion> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(version)); page.setTotal(1); return page;
        });

        var result = service().search("structure-versions", "structure", "PUBLISHED", 1, 20);

        assertThat(result.getRecords().getFirst().assetKey()).isEqualTo("order:v2");
        assertThat(result.getRecords().getFirst().detail().path("referenceCode").asText())
                .isEqualTo("order:v2");
        assertThat(result.getRecords().getFirst().detail().path("parentReferenceCode").asText())
                .isEqualTo("order");
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotQueryVersionsWithoutVisibleParents() {
        when(structures.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());

        var result = service().search("structure-versions", null, null, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(structureVersions, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void reportsLifecycleReadyButNeverAutoEnablesPublicRuntime() {
        when(structures.selectCount(null)).thenReturn(1L); when(mappings.selectCount(null)).thenReturn(1L);
        when(connectors.selectCount(null)).thenReturn(1L); when(routes.selectCount(null)).thenReturn(1L);
        when(releases.selectCount(null)).thenReturn(1L); when(products.selectCount(null)).thenReturn(1L);
        when(applications.selectCount(null)).thenReturn(1L); when(subscriptions.selectCount(null)).thenReturn(1L);
        when(snapshots.selectCount(null)).thenReturn(1L);
        var service = service(); ReflectionTestUtils.setField(service, "publicRuntimeEnabled", false);

        var result = service.readiness();

        assertThat(result.ready()).isTrue();
        assertThat(result.publicRuntimeEnabled()).isFalse();
        assertThat(result.blockers()).contains("公共运行时仍处于关闭状态");
    }

    private OpenApiLifecycleCatalogService service() {
        return new OpenApiLifecycleCatalogService(structures, structureVersions, mappings, mappingVersions,
                valueMaps, valueMapVersions, connectors, connectorVersions, routes, routeVersions, releases,
                orchestrations, orchestrationVersions, callbacks, callbackVersions, products, productVersions,
                applications, applicationClients, subscriptions, approvals, policies, snapshots, new ObjectMapper());
    }
}
