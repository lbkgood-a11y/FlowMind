package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ActiveRelease;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.infrastructure.mapper.ActiveReleaseMapper;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingRuleMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ReleaseSnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseManagementServiceTest {

    @Mock private RouteDefinitionMapper routeDefinitionMapper;
    @Mock private RouteVersionMapper routeVersionMapper;
    @Mock private ConnectorVersionMapper connectorVersionMapper;
    @Mock private MappingVersionMapper mappingVersionMapper;
    @Mock private MappingRuleMapper mappingRuleMapper;
    @Mock private ValueMapVersionMapper valueMapVersionMapper;
    @Mock private OrchestrationVersionMapper orchestrationVersionMapper;
    @Mock private ReleaseSnapshotMapper releaseSnapshotMapper;
    @Mock private ActiveReleaseMapper activeReleaseMapper;
    @Mock private CompiledReleaseCache cache;
    @Mock private IntegrationAuditService auditService;
    private ReleaseManagementService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseManagementService(routeDefinitionMapper, routeVersionMapper,
                connectorVersionMapper, mappingVersionMapper, mappingRuleMapper, valueMapVersionMapper,
                orchestrationVersionMapper, releaseSnapshotMapper, activeReleaseMapper, cache,
                auditService, new ObjectMapper(), new RoutePlanCompiler(new ObjectMapper()));
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void publishesImmutableSnapshotPinningConnectorAndSecretReference() {
        when(routeVersionMapper.selectById("route-v1")).thenReturn(routeVersion());
        when(routeDefinitionMapper.selectById("route-1")).thenReturn(route());
        when(connectorVersionMapper.selectById("connector-v1")).thenReturn(connector());
        when(releaseSnapshotMapper.nextReleaseNumber("route-1", "PROD")).thenReturn(1);

        var response = service.publish("route-v1", "initial production release");

        assertThat(response.releaseNumber()).isEqualTo(1);
        assertThat(response.pinnedDependencies().path("connectorVersionId").asText())
                .isEqualTo("connector-v1");
        assertThat(response.pinnedDependencies().path("secretReference").asText())
                .isEqualTo("vault:partner/api-v1");
        assertThat(response.snapshotHash()).isNotBlank();
        verify(releaseSnapshotMapper).lockReleaseSeries("route-1:PROD");
        verify(releaseSnapshotMapper).insert(any(ReleaseSnapshot.class));
    }

    @Test
    void activationFailsClosedOnOptimisticConflict() {
        ReleaseSnapshot snapshot = snapshot("release-2");
        when(releaseSnapshotMapper.selectById("release-2")).thenReturn(snapshot);
        when(routeDefinitionMapper.selectById("route-1")).thenReturn(route());
        ActiveRelease current = new ActiveRelease();
        current.setReleaseSnapshotId("release-1");
        current.setPolicyVersion(7L);
        current.setRowVersion(3L);
        when(activeReleaseMapper.find("route-1", "PROD")).thenReturn(current);
        when(activeReleaseMapper.compareAndSet(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.activate("release-2"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_RELEASE_ACTIVATION_CONFLICT");
    }

    @Test
    void fallsBackToPostgresWhenCompiledCacheMisses() {
        when(cache.get("tenant-a", Environment.PROD, "orders.submit")).thenReturn(Optional.empty());
        when(routeDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(route());
        ActiveRelease active = new ActiveRelease();
        active.setReleaseSnapshotId("release-1");
        active.setPolicyVersion(3L);
        when(activeReleaseMapper.find("route-1", "PROD")).thenReturn(active);
        when(releaseSnapshotMapper.selectById("release-1")).thenReturn(snapshot("release-1"));

        var result = service.resolveActive("orders.submit", Environment.PROD);

        assertThat(result.releaseId()).isEqualTo("release-1");
        assertThat(result.policyVersion()).isEqualTo(3L);
        verify(cache).put(result);
    }

    private RouteDefinition route() {
        RouteDefinition route = new RouteDefinition();
        route.setId("route-1");
        route.setTenantId("tenant-a");
        route.setRouteKey("orders.submit");
        return route;
    }

    private RouteVersion routeVersion() {
        RouteVersion route = new RouteVersion();
        route.setId("route-v1");
        route.setRouteDefinitionId("route-1");
        route.setEnvironment(Environment.PROD);
        route.setLifecycleState(VersionLifecycleState.PUBLISHED);
        route.setPriority(10);
        route.setEnabled(true);
        route.setRoutePredicate(new ObjectMapper().createObjectNode());
        route.setExecutionMode(ExecutionMode.SYNCHRONOUS);
        route.setConnectorVersionId("connector-v1");
        return route;
    }

    private ConnectorVersion connector() {
        ConnectorVersion connector = new ConnectorVersion();
        connector.setId("connector-v1");
        connector.setLifecycleState(VersionLifecycleState.PUBLISHED);
        connector.setAuthenticationType(AuthenticationType.API_KEY);
        connector.setSecretReference("vault:partner/api-v1");
        return connector;
    }

    private ReleaseSnapshot snapshot(String id) {
        ReleaseSnapshot snapshot = new ReleaseSnapshot();
        snapshot.setId(id);
        snapshot.setTenantId("tenant-a");
        snapshot.setEnvironment(Environment.PROD);
        snapshot.setRouteDefinitionId("route-1");
        snapshot.setRouteVersionId("route-v1");
        snapshot.setLifecycleState(VersionLifecycleState.PUBLISHED);
        snapshot.setSnapshotHash("hash");
        snapshot.setPinnedDependencies(new ObjectMapper().createObjectNode().put("routeVersionId", "route-v1"));
        return snapshot;
    }
}
