package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.integration.IntegrationAdmissionDecision;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import com.triobase.service.openapi.domain.entity.ApiProductVersion;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.CredentialBinding;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import com.triobase.service.openapi.domain.entity.ProductSubscription;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CredentialBindingState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.EffectiveTrafficPolicy;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CredentialBindingMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
@Service @RequiredArgsConstructor
public class IntegrationAdmissionService {
 private final ApplicationClientMapper clientMapper;private final CredentialBindingMapper bindingMapper;private final ApiProductVersionMapper productVersionMapper;
 private final CredentialProvider credentialProvider;private final ProductSubscriptionService subscriptionService;private final TrafficPolicyService trafficPolicyService;
 private final PolicySnapshotService snapshotService;private final NetworkPolicyMatcher networkMatcher;private final StringRedisTemplate redis;
 private final IntegrationAuditService auditService;private final PolicyChangeNotifier policyNotifier;private final CallbackProfileService callbackProfileService;
 @Transactional public IntegrationAdmissionDecision admit(IntegrationAdmissionRequest r){try{
  validate(r);Environment env=Environment.valueOf(r.environment().toUpperCase(Locale.ROOT));snapshotService.requireCurrent("platform-gateway",r.tenantId(),env);
  ApplicationClient client=clientMapper.selectOne(new LambdaQueryWrapper<ApplicationClient>().eq(ApplicationClient::getTenantId,r.tenantId()).eq(ApplicationClient::getEnvironment,env).eq(ApplicationClient::getClientKey,r.clientKey()));
  if(client==null||client.getLifecycleState()!=ApplicationLifecycleState.ACTIVE||(client.getExpiresAt()!=null&&!client.getExpiresAt().isAfter(LocalDateTime.now())))return deny("ACCESS_DENIED",401,0);
  if(r.callback())return admitCallback(r,env,client);
  if(!authenticate(client,r)){violation(client,"AUTHENTICATION_FAILED",null);return deny("ACCESS_DENIED",401,0);}
  ProductSubscription subscription=subscriptionService.requireActiveAccess(client.getId(),r.routeKey(),r.operation(),r.scopes()==null?Set.of():r.scopes(),LocalDateTime.now());
  ApiProductVersion productVersion=productVersionMapper.selectById(subscription.getApiProductVersionId());
  EffectiveTrafficPolicy effective=trafficPolicyService.resolve(r.tenantId(),env,client.getId(),productVersion.getApiProductId(),r.routeKey(),r.operation(),subscription.getId());
  return enforce(r,env,client,subscription.getId(),effective.policy());
 }catch(BizException e){int status=e.getCode()>=50300?503:403;return deny(status==503?"POLICY_UNAVAILABLE":"ACCESS_DENIED",status,0);}catch(Exception e){return deny("ADMISSION_UNAVAILABLE",503,0);}}
 private IntegrationAdmissionDecision admitCallback(IntegrationAdmissionRequest r,Environment env,ApplicationClient client){CallbackProfileService.PublishedCallback callback=callbackProfileService.resolvePublished(r.routeKey(),r.tenantId(),env);if(!client.getId().equals(callback.version().getApplicationClientId()))return deny("ACCESS_DENIED",403,0);JsonNode policy=callback.version().getSecurityPolicy().deepCopy();if(policy.isObject()){((com.fasterxml.jackson.databind.node.ObjectNode)policy).put("maxBodyBytes",callback.version().getMaxBodyBytes());}return enforce(r,env,client,"CALLBACK:"+callback.version().getId(),policy);}
 private IntegrationAdmissionDecision enforce(IntegrationAdmissionRequest r,Environment env,ApplicationClient client,String subscriptionId,JsonNode policy){if(policy.path("requireTls").asBoolean(false)&&!r.tls())return deny("TLS_REQUIRED",403,0);long maxBody=policy.path("maxBodyBytes").asLong(Long.MAX_VALUE);if(r.contentLength()>maxBody)return deny("REQUEST_TOO_LARGE",413,0);if(!sourceAllowed(r.sourceIp(),policy.path("allowedNetworks"))){violation(client,"SOURCE_NETWORK_DENIED",policy);return deny("ACCESS_DENIED",403,0);}long retry=consumeLimits(r.tenantId(),client.getId(),r.routeKey(),policy);if(retry>0)return deny("RATE_LIMITED",429,retry);PolicyEnforcementState state=snapshotService.status("platform-gateway",r.tenantId(),env);return new IntegrationAdmissionDecision(true,200,"ALLOWED",r.tenantId(),client.getId(),subscriptionId,state.getAppliedPolicyVersion(),0,maxBody,policy.path("maxConcurrency").asLong(100),policy.path("maxActiveWorkflows").asLong(20));}
 private boolean authenticate(ApplicationClient client,IntegrationAdmissionRequest r){List<CredentialBinding> bindings=bindingMapper.selectList(new LambdaQueryWrapper<CredentialBinding>().eq(CredentialBinding::getApplicationClientId,client.getId()).in(CredentialBinding::getLifecycleState,CredentialBindingState.ACTIVE,CredentialBindingState.RETIRING));LocalDateTime now=LocalDateTime.now();return bindings.stream().filter(b->!now.isBefore(b.getValidFrom())&&(b.getExpiresAt()==null||now.isBefore(b.getExpiresAt()))&&(b.getRetirementAt()==null||now.isBefore(b.getRetirementAt()))).anyMatch(b->{try{return verify(b.getAuthenticationType(),credentialProvider.resolve(b.getSecretReference()),r);}catch(Exception e){return false;}});}
 private boolean verify(AuthenticationType type,CredentialMaterial material,IntegrationAdmissionRequest r)throws Exception{return switch(type){case API_KEY->constant(material.required("apiKey"),r.credential());case BASIC->{String expected="Basic "+Base64.getEncoder().encodeToString((material.required("username")+":"+material.required("password")).getBytes(StandardCharsets.UTF_8));yield constant(expected,r.credential());}case OAUTH2_CLIENT->constant("Bearer "+material.required("accessToken"),r.credential());case HMAC->{if(!validReplay(r,material))yield false;Mac mac=Mac.getInstance(material.values().getOrDefault("algorithm","HmacSHA256"));mac.init(new SecretKeySpec(material.required("secret").getBytes(StandardCharsets.UTF_8),mac.getAlgorithm()));String payload=r.timestamp()+"."+r.nonce()+"."+(r.bodyHash()==null?"":r.bodyHash());yield constant(Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))),r.signature());}case RSA->{if(!validReplay(r,material))yield false;byte[] key=Base64.getDecoder().decode(material.required("publicKeyX509Base64"));Signature verifier=Signature.getInstance(material.values().getOrDefault("algorithm","SHA256withRSA"));verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));verifier.update((r.timestamp()+"."+r.nonce()+"."+(r.bodyHash()==null?"":r.bodyHash())).getBytes(StandardCharsets.UTF_8));yield verifier.verify(Base64.getDecoder().decode(r.signature()));}case MTLS->constant(material.required("certificateFingerprint"),r.clientCertificateFingerprint());default->false;};}
 private boolean validReplay(IntegrationAdmissionRequest r,CredentialMaterial m){if(!StringUtils.hasText(r.timestamp())||!StringUtils.hasText(r.nonce())||!StringUtils.hasText(r.signature()))return false;long window=Long.parseLong(m.values().getOrDefault("signatureWindowSeconds","300"));long ts=Long.parseLong(r.timestamp());if(Math.abs(Instant.now().getEpochSecond()-ts)>window)return false;Boolean fresh=redis.opsForValue().setIfAbsent("openapi:nonce:"+r.tenantId()+":"+r.clientKey()+":"+r.nonce(),"1",Duration.ofSeconds(window));return Boolean.TRUE.equals(fresh);}
 private long consumeLimits(String tenant,String client,String route,JsonNode p){try{long second=p.path("ratePerSecond").asLong(0);if(second>0&&increment("openapi:rate:"+tenant+":"+client+":"+route+":"+Instant.now().getEpochSecond(),Duration.ofSeconds(2))>second)return 1;long daily=p.path("dailyQuota").asLong(0);if(daily>0&&increment("openapi:daily:"+tenant+":"+client+":"+route+":"+java.time.LocalDate.now(),Duration.ofDays(2))>daily)return 60;return 0;}catch(Exception e){throw new BizException(50392,"OPENAPI_RATE_LIMIT_STATE_UNAVAILABLE");}}
 private long increment(String key,Duration ttl){Long value=redis.opsForValue().increment(key);if(value!=null&&value==1)redis.expire(key,ttl);return value==null?Long.MAX_VALUE:value;}
 private boolean sourceAllowed(String ip,JsonNode networks){if(networks==null||!networks.isArray()||networks.isEmpty())return true;for(JsonNode n:networks)if(networkMatcher.matches(ip,n.asText()))return true;return false;}
 private void violation(ApplicationClient client,String reason,JsonNode policy){int count=client.getSecurityViolationCount()==null?1:client.getSecurityViolationCount()+1;client.setSecurityViolationCount(count);int threshold=policy==null?10:policy.path("securityViolationThreshold").asInt(10);if(count>=threshold){client.setLifecycleState(ApplicationLifecycleState.SUSPENDED);client.setSuspensionReason("AUTOMATIC_SECURITY_SUSPENSION");policyNotifier.publishAfterCommit(client.getTenantId(),client.getEnvironment());}client.setUpdatedBy("SYSTEM");client.setUpdatedAt(LocalDateTime.now());clientMapper.updateById(client);auditService.success("APPLICATION_SECURITY_VIOLATION","APPLICATION_CLIENT",client.getId(),JsonNodeFactory.instance.objectNode().put("reason",reason).put("count",count));}
 private void validate(IntegrationAdmissionRequest r){if(r==null||!StringUtils.hasText(r.tenantId())||!StringUtils.hasText(r.clientKey())||!StringUtils.hasText(r.environment())||!StringUtils.hasText(r.routeKey())||!StringUtils.hasText(r.operation())||r.contentLength()<0)throw new BizException(40092,"OPENAPI_ADMISSION_REQUEST_INVALID");}private boolean constant(String expected,String actual){return actual!=null&&MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}private IntegrationAdmissionDecision deny(String reason,int status,long retry){return IntegrationAdmissionDecision.deny(status,reason,retry);}
}
