package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.entity.ExecutionStepAttempt;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.RouteVersion;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import com.triobase.service.openapi.dto.SyncInvocationResponse;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ExecutionStepAttemptMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.RouteVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.OutboundIntegrationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Service @RequiredArgsConstructor
public class SynchronousInvocationService {
 private final ReleaseManagementService releases;private final RouteVersionMapper routeMapper;private final ConnectorVersionMapper connectorMapper;
 private final CompiledMappingExecutor mappingExecutor;private final CredentialProvider credentialProvider;private final OutboundIntegrationClient outbound;
 private final IntegrationExecutionMapper executionMapper;private final ExecutionStepAttemptMapper attemptMapper;private final RuntimeBudgetService budgets;
 private final IntegrationAuditService auditService;private final ObjectMapper objectMapper;
 public SyncInvocationResponse invoke(String routeKey,Environment environment,String applicationClientId,String subscriptionId,long maxConcurrency,JsonNode canonicalRequest){if(!StringUtils.hasText(applicationClientId)||!StringUtils.hasText(subscriptionId))throw new BizException(40130,"OPENAPI_RUNTIME_ADMISSION_CONTEXT_REQUIRED");CompiledRouteRelease release=releases.resolveActive(routeKey,environment);RouteVersion route=routeMapper.selectById(release.routeVersionId());ConnectorVersion connector=route==null?null:connectorMapper.selectById(route.getConnectorVersionId());requireEligible(route,connector);IntegrationExecution execution=start(release,environment,applicationClientId);long started=System.nanoTime();try(RuntimeBudgetService.BudgetLease ignored=budgets.acquireRequest(release.tenantId(),applicationClientId,routeKey,Math.max(1,maxConcurrency))){JsonNode externalRequest=mappingExecutor.execute(route.getRequestMappingVersionId(),canonicalRequest==null?objectMapper.createObjectNode():canonicalRequest);CredentialMaterial credential=connector.getAuthenticationType()==com.triobase.service.openapi.domain.enums.AuthenticationType.NONE?null:credentialProvider.resolve(connector.getSecretReference());LocalDateTime callStarted=LocalDateTime.now();OutboundIntegrationClient.OutboundResponse partner=outbound.execute(new OutboundIntegrationClient.OutboundRequest(connector,externalRequest,Map.of(),credential));recordAttempt(execution.getId(),partner,callStarted);if(partner.status()<200||partner.status()>=300)throw new BizException(50240,"OPENAPI_PARTNER_HTTP_ERROR:"+partner.status());JsonNode externalResponse=parse(partner.body());JsonNode canonicalResponse=mappingExecutor.execute(route.getResponseMappingVersionId(),externalResponse);long duration=java.time.Duration.ofNanos(System.nanoTime()-started).toMillis();succeed(execution,duration);auditService.success("SYNCHRONOUS_INVOCATION_SUCCEEDED","EXECUTION",execution.getId(),JsonNodeFactory.instance.objectNode().put("routeKey",routeKey).put("partnerStatus",partner.status()));return new SyncInvocationResponse(execution.getId(),partner.status(),canonicalResponse,TraceUtil.getTraceId(),duration);}catch(BizException e){fail(execution,e,System.nanoTime()-started);throw e;}catch(Exception e){BizException normalized=new BizException(50241,"OPENAPI_SYNCHRONOUS_INVOCATION_FAILED");fail(execution,normalized,System.nanoTime()-started);throw normalized;}}
 private void requireEligible(RouteVersion route,ConnectorVersion connector){if(route==null||route.getExecutionMode()!=ExecutionMode.SYNCHRONOUS||StringUtils.hasText(route.getOrchestrationVersionId())||!StringUtils.hasText(route.getRequestMappingVersionId())||!StringUtils.hasText(route.getResponseMappingVersionId())||connector==null||connector.getOperationClass()!=ConnectorOperationClass.READ_ONLY||connector.getTimeoutMillis()==null||connector.getTimeoutMillis()>=500)throw new BizException(40930,"OPENAPI_ROUTE_NOT_SYNCHRONOUSLY_ELIGIBLE");}
 private IntegrationExecution start(CompiledRouteRelease r,Environment env,String client){LocalDateTime now=LocalDateTime.now();IntegrationExecution e=new IntegrationExecution();e.setId(UlidGenerator.nextUlid());e.setTenantId(r.tenantId());e.setEnvironment(env);e.setApplicationClientId(client);e.setRouteDefinitionId(r.routeId());e.setReleaseSnapshotId(r.releaseId());e.setExecutionMode(ExecutionMode.SYNCHRONOUS);e.setExecutionState(ExecutionState.RUNNING);e.setTraceId(TraceUtil.getTraceId());e.setCallerId(client);e.setStartedAt(now);e.setDiagnosticEnabled(false);e.setRetentionUntil(now.plusDays(180));e.setRowVersion(0L);e.setCreatedAt(now);e.setUpdatedAt(now);executionMapper.insert(e);return e;}
 private void recordAttempt(String executionId,OutboundIntegrationClient.OutboundResponse response,LocalDateTime started){ExecutionStepAttempt a=new ExecutionStepAttempt();a.setId(UlidGenerator.nextUlid());a.setExecutionId(executionId);a.setStepKey("connector");a.setStepType("HTTP_INVOKE");a.setAttemptNumber(1);a.setAttemptState(response.status()>=200&&response.status()<300?"SUCCEEDED":"FAILED");a.setStartedAt(started);a.setCompletedAt(LocalDateTime.now());a.setDurationMillis(response.durationMillis());a.setExternalStatus(response.status());a.setEvidence(JsonNodeFactory.instance.objectNode().put("responseBytes",response.body().length));a.setCreatedAt(LocalDateTime.now());attemptMapper.insert(a);}
 private JsonNode parse(byte[] body){try{return body==null||body.length==0?objectMapper.createObjectNode():objectMapper.readTree(new String(body,StandardCharsets.UTF_8));}catch(Exception e){throw new BizException(50242,"OPENAPI_PARTNER_RESPONSE_NOT_JSON");}}
 private void succeed(IntegrationExecution e,long duration){e.setExecutionState(ExecutionState.SUCCEEDED);e.setCompletedAt(LocalDateTime.now());e.setDurationMillis(duration);e.setUpdatedAt(LocalDateTime.now());executionMapper.updateById(e);}private void fail(IntegrationExecution e,BizException error,long nanos){e.setExecutionState(ExecutionState.FAILED);e.setCompletedAt(LocalDateTime.now());e.setDurationMillis(java.time.Duration.ofNanos(nanos).toMillis());e.setErrorCode(String.valueOf(error.getCode()));e.setSanitizedError(sanitize(error.getMessage()));e.setUpdatedAt(LocalDateTime.now());executionMapper.updateById(e);auditService.failure("SYNCHRONOUS_INVOCATION_FAILED","EXECUTION",e.getId(),e.getSanitizedError(),JsonNodeFactory.instance.objectNode());}private String sanitize(String s){if(s==null)return null;String value=s.replaceAll("(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+","$1 ***");return value.length()>512?value.substring(0,512):value;}
}
