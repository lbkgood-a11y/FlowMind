package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.RouteDefinition;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.RouteResolutionContext;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.OrchestrationVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteDefinitionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteRegistryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private RouteDefinitionMapper definitionMapper;
    @Mock private RouteVersionMapper versionMapper;
    @Mock private ConnectorVersionMapper connectorVersionMapper;
    @Mock private MappingVersionMapper mappingVersionMapper;
    @Mock private OrchestrationVersionMapper orchestrationVersionMapper;
    @Mock private IntegrationAuditService auditService;
    private RouteRegistryService service;

    @BeforeEach
    void setUp() {
        service = new RouteRegistryService(definitionMapper, versionMapper, connectorVersionMapper,
                mappingVersionMapper, orchestrationVersionMapper,
                new RoutePredicateEvaluator(), new RoutePlanCompiler(objectMapper), auditService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void resolvesHighestPriorityMatchingPublishedRoute() throws Exception {
        when(definitionMapper.selectOne(any(Wrapper.class))).thenReturn(definition());
        RouteVersion low = published("low", 5, "CN");
        RouteVersion high = published("high", 10, "CN");
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(low, high));

        var result = service.resolve("orders.submit", Environment.PROD,
                new RouteResolutionContext(LocalDateTime.parse("2026-07-16T10:00:00"),
                        Map.of("x-region", "CN"), Map.of(), Map.of()));

        assertThat(result.routeVersionId()).isEqualTo("high");
    }

    @Test
    void blocksPublicationWhenEqualPriorityPredicatesCanOverlap() throws Exception {
        RouteVersion draft = published("draft", 10, "CN");
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        when(versionMapper.selectById("draft")).thenReturn(draft);
        when(definitionMapper.selectById("route-1")).thenReturn(definition());
        when(connectorVersionMapper.selectById("connector-v1")).thenReturn(connector());
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(published("existing", 10, "CN")));

        assertThatThrownBy(() -> service.publish("draft"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_ROUTE_PUBLICATION_AMBIGUOUS");
    }

    @Test
    void allowsEqualPriorityWhenPredicatesAreDisjoint() throws Exception {
        RouteVersion draft = published("draft", 10, "CN");
        draft.setLifecycleState(VersionLifecycleState.DRAFT);
        when(versionMapper.selectById("draft")).thenReturn(draft);
        when(definitionMapper.selectById("route-1")).thenReturn(definition());
        when(connectorVersionMapper.selectById("connector-v1")).thenReturn(connector());
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(published("existing", 10, "US")));
        when(versionMapper.updateById(any(RouteVersion.class))).thenReturn(1);

        assertThat(service.publish("draft").lifecycleState()).isEqualTo(VersionLifecycleState.PUBLISHED);
    }

    private RouteDefinition definition() {
        RouteDefinition route = new RouteDefinition();
        route.setId("route-1");
        route.setTenantId("tenant-a");
        route.setRouteKey("orders.submit");
        route.setDisplayName("Submit order");
        route.setLifecycleState(AssetLifecycleState.ACTIVE);
        return route;
    }

    private RouteVersion published(String id, int priority, String region) throws Exception {
        RouteVersion version = new RouteVersion();
        version.setId(id);
        version.setRouteDefinitionId("route-1");
        version.setVersionNumber(id.equals("high") ? 2 : 1);
        version.setEnvironment(Environment.PROD);
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPriority(priority);
        version.setEnabled(true);
        version.setRoutePredicate(objectMapper.readTree("{\"all\":[{\"source\":\"HEADER\",\"name\":\"x-region\",\"operator\":\"EQUALS\",\"value\":\"" + region + "\"}]}"));
        version.setExecutionMode(ExecutionMode.SYNCHRONOUS);
        version.setConnectorVersionId("connector-v1");
        return version;
    }

    private ConnectorVersion connector() {
        ConnectorVersion connector = new ConnectorVersion();
        connector.setId("connector-v1");
        connector.setLifecycleState(VersionLifecycleState.PUBLISHED);
        connector.setAuthenticationType(AuthenticationType.NONE);
        connector.setOperationClass(ConnectorOperationClass.READ_ONLY);
        return connector;
    }
}
