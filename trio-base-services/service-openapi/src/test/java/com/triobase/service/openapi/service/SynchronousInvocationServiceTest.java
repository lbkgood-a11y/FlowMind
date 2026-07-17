package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.*;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.OutboundIntegrationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class SynchronousInvocationServiceTest {
 @Mock ReleaseManagementService releases;@Mock RouteVersionMapper routes;@Mock ConnectorVersionMapper connectors;@Mock CompiledMappingExecutor mapping;@Mock CredentialProvider credentials;@Mock OutboundIntegrationClient outbound;@Mock IntegrationExecutionMapper executions;@Mock ExecutionStepAttemptMapper attempts;@Mock RuntimeBudgetService budgets;@Mock RuntimeBudgetService.BudgetLease lease;@Mock IntegrationAuditService audit;ObjectMapper json=new ObjectMapper();SynchronousInvocationService service;
 @BeforeEach void setUp(){service=new SynchronousInvocationService(releases,routes,connectors,mapping,credentials,outbound,executions,attempts,budgets,audit,json);TraceUtil.setTraceId("trace-sync-1");}@AfterEach void clear(){TraceUtil.clear();}
 @Test void executesValidatedSingleReadOnlyPartnerCall()throws Exception{arrangeEligible();var canonicalRequest=json.readTree("{\"id\":\"1\"}");var externalRequest=json.readTree("{\"externalId\":\"1\"}");var externalResponse=json.readTree("{\"result\":\"ok\"}");var canonicalResponse=json.readTree("{\"status\":\"ok\"}");when(mapping.execute("request-map",canonicalRequest)).thenReturn(externalRequest);when(outbound.execute(any())).thenReturn(new OutboundIntegrationClient.OutboundResponse(200,Map.of(),externalResponse.toString().getBytes(StandardCharsets.UTF_8),12));when(mapping.execute("response-map",externalResponse)).thenReturn(canonicalResponse);var result=service.invoke("orders.get",Environment.PROD,"client-1","sub-1",3,canonicalRequest);assertThat(result.partnerStatus()).isEqualTo(200);assertThat(result.body()).isEqualTo(canonicalResponse);assertThat(result.traceId()).isEqualTo("trace-sync-1");ArgumentCaptor<IntegrationExecution> execution=ArgumentCaptor.forClass(IntegrationExecution.class);verify(executions).insert(execution.capture());assertThat(execution.getValue().getExecutionState()).isEqualTo(ExecutionState.SUCCEEDED);verify(attempts).insert(any(com.triobase.service.openapi.domain.entity.ExecutionStepAttempt.class));}
 @Test void rejectsStateChangingOrSlowConnectors(){arrangeBase();ConnectorVersion connector=connector();connector.setOperationClass(ConnectorOperationClass.STATE_CHANGING);connector.setTimeoutMillis(1000);when(connectors.selectById("connector-v1")).thenReturn(connector);assertThatThrownBy(()->service.invoke("orders.get",Environment.PROD,"client-1","sub-1",3,json.createObjectNode())).isInstanceOf(BizException.class).hasMessage("OPENAPI_ROUTE_NOT_SYNCHRONOUSLY_ELIGIBLE");}
 @Test void normalizesPartnerErrorsAndPersistsFailedSummary(){arrangeEligible();when(mapping.execute(any(),any())).thenReturn(json.createObjectNode());when(outbound.execute(any())).thenReturn(new OutboundIntegrationClient.OutboundResponse(503,Map.of(),new byte[0],20));assertThatThrownBy(()->service.invoke("orders.get",Environment.PROD,"client-1","sub-1",3,json.createObjectNode())).isInstanceOf(BizException.class).hasMessage("OPENAPI_PARTNER_HTTP_ERROR:503");ArgumentCaptor<IntegrationExecution> updated=ArgumentCaptor.forClass(IntegrationExecution.class);verify(executions).updateById(updated.capture());assertThat(updated.getAllValues().getLast().getExecutionState()).isEqualTo(ExecutionState.FAILED);assertThat(updated.getAllValues().getLast().getSanitizedError()).doesNotContain("Bearer");}
 @Test void quotaExhaustionStopsBeforeNetworkCall(){arrangeBase();when(connectors.selectById("connector-v1")).thenReturn(connector());when(budgets.acquireRequest(any(),any(),any(),any(Long.class))).thenThrow(new BizException(42901,"OPENAPI_RUNTIME_CONCURRENCY_EXHAUSTED"));assertThatThrownBy(()->service.invoke("orders.get",Environment.PROD,"client-1","sub-1",1,json.createObjectNode())).isInstanceOf(BizException.class).hasMessage("OPENAPI_RUNTIME_CONCURRENCY_EXHAUSTED");}
 @Test void routeMissDoesNotCreateExecution(){when(releases.resolveActive("missing",Environment.PROD)).thenThrow(new BizException(40445,"OPENAPI_ACTIVE_RELEASE_NOT_FOUND"));assertThatThrownBy(()->service.invoke("missing",Environment.PROD,"client-1","sub-1",1,json.createObjectNode())).isInstanceOf(BizException.class).hasMessage("OPENAPI_ACTIVE_RELEASE_NOT_FOUND");org.mockito.Mockito.verifyNoInteractions(executions,outbound);}
 private void arrangeEligible(){arrangeBase();when(connectors.selectById("connector-v1")).thenReturn(connector());when(budgets.acquireRequest(any(),any(),any(),any(Long.class))).thenReturn(lease);}private void arrangeBase(){CompiledRouteRelease release=new CompiledRouteRelease("tenant-a",Environment.PROD,"orders.get","route-1","route-v1","release-1",1,"hash",json.createObjectNode());when(releases.resolveActive("orders.get",Environment.PROD)).thenReturn(release);RouteVersion route=new RouteVersion();route.setId("route-v1");route.setExecutionMode(ExecutionMode.SYNCHRONOUS);route.setConnectorVersionId("connector-v1");route.setRequestMappingVersionId("request-map");route.setResponseMappingVersionId("response-map");when(routes.selectById("route-v1")).thenReturn(route);}private ConnectorVersion connector(){ConnectorVersion c=new ConnectorVersion();c.setId("connector-v1");c.setOperationClass(ConnectorOperationClass.READ_ONLY);c.setTimeoutMillis(400);c.setAuthenticationType(AuthenticationType.NONE);return c;}
}
