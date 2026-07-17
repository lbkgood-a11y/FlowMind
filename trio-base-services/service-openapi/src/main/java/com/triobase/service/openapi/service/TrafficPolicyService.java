package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.TrafficPolicyVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.PolicyScopeType;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateTrafficPolicyRequest;
import com.triobase.service.openapi.dto.EffectiveTrafficPolicy;
import com.triobase.service.openapi.infrastructure.mapper.TrafficPolicyVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service @RequiredArgsConstructor
public class TrafficPolicyService {
 private static final Set<String> LIMITS=Set.of("ratePerSecond","burst","dailyQuota","maxBodyBytes","maxConcurrency","maxActiveWorkflows","callbackPerMinute","signatureWindowSeconds");
 private static final Set<String> ALLOWED=Set.of("ratePerSecond","burst","dailyQuota","maxBodyBytes","maxConcurrency","maxActiveWorkflows","callbackPerMinute","signatureWindowSeconds","requireTls","queueOnConcurrency","allowedNetworks","authenticationTypes","sensitiveFieldPolicy","securityViolationThreshold");
 private final TrafficPolicyVersionMapper mapper;private final ObjectMapper objectMapper;private final IntegrationAuditService auditService;private final PolicyChangeNotifier policyNotifier;
 @Transactional public TrafficPolicyVersion createDraft(CreateTrafficPolicyRequest r){validate(r);String tenant=targetTenant(r.tenantId());TrafficPolicyVersion latest=mapper.selectOne(new LambdaQueryWrapper<TrafficPolicyVersion>().eq(TrafficPolicyVersion::getEnvironment,r.environment()).eq(TrafficPolicyVersion::getScopeType,r.scopeType()).eq(TrafficPolicyVersion::getScopeId,r.scopeId()).orderByDesc(TrafficPolicyVersion::getPolicyVersion).last("LIMIT 1"));if(mapper.selectCount(new LambdaQueryWrapper<TrafficPolicyVersion>().eq(TrafficPolicyVersion::getEnvironment,r.environment()).eq(TrafficPolicyVersion::getScopeType,r.scopeType()).eq(TrafficPolicyVersion::getScopeId,r.scopeId()).eq(TrafficPolicyVersion::getLifecycleState,VersionLifecycleState.DRAFT))>0)throw new BizException(40990,"OPENAPI_POLICY_DRAFT_EXISTS");TrafficPolicyVersion v=new TrafficPolicyVersion();v.setId(UlidGenerator.nextUlid());v.setTenantId(tenant);v.setEnvironment(r.environment());v.setScopeType(r.scopeType());v.setScopeId(r.scopeId());v.setPolicyVersion(latest==null?1:latest.getPolicyVersion()+1);v.setLifecycleState(VersionLifecycleState.DRAFT);v.setPolicyContent(r.policyContent().deepCopy());v.setContentHash(hash(v.getPolicyContent()));v.setRowVersion(0L);initialize(v);mapper.insert(v);return v;}
 @Transactional public TrafficPolicyVersion publish(String id){TrafficPolicyVersion v=require(id);if(v.getLifecycleState()!=VersionLifecycleState.DRAFT)throw new BizException(40990,"OPENAPI_POLICY_NOT_DRAFT");validateContent(v.getPolicyContent());v.setLifecycleState(VersionLifecycleState.PUBLISHED);v.setPublishedBy(operator());v.setPublishedAt(LocalDateTime.now());touch(v);mapper.updateById(v);auditService.success("TRAFFIC_POLICY_PUBLISHED","TRAFFIC_POLICY",id,objectMapper.createObjectNode().put("policyVersion",v.getPolicyVersion()));policyNotifier.publishAfterCommit(v.getTenantId(),v.getEnvironment());return v;}
 public EffectiveTrafficPolicy resolve(String tenantId,Environment env,String clientId,String productId,String routeKey,String operation,String subscriptionId){String tenant=tenantId==null?SecurityContextHolder.getTenantId():tenantId;Map<PolicyScopeType,String> scopes=new HashMap<>();scopes.put(PolicyScopeType.TENANT,tenant);put(scopes,PolicyScopeType.CLIENT,clientId);put(scopes,PolicyScopeType.PRODUCT,productId);put(scopes,PolicyScopeType.ROUTE,routeKey);put(scopes,PolicyScopeType.OPERATION,StringUtils.hasText(routeKey)&&StringUtils.hasText(operation)?routeKey+":"+operation:null);put(scopes,PolicyScopeType.SUBSCRIPTION,subscriptionId);List<TrafficPolicyVersion> all=mapper.selectList(new LambdaQueryWrapper<TrafficPolicyVersion>().eq(TrafficPolicyVersion::getEnvironment,env).eq(TrafficPolicyVersion::getLifecycleState,VersionLifecycleState.PUBLISHED));List<TrafficPolicyVersion> applied=all.stream().filter(v->scopes.get(v.getScopeType())!=null&&scopes.get(v.getScopeType()).equals(v.getScopeId())).sorted(Comparator.comparingInt(v->v.getScopeType().ordinal())).toList();ObjectNode effective=objectMapper.createObjectNode();for(TrafficPolicyVersion v:applied)merge(effective,v.getPolicyContent());return new EffectiveTrafficPolicy(tenant,env,clientId,productId,routeKey,operation,effective,applied.stream().map(TrafficPolicyVersion::getId).toList());}
 private void merge(ObjectNode target,JsonNode source){if(source==null||!source.isObject())return;source.fields().forEachRemaining(e->{String key=e.getKey();JsonNode value=e.getValue();if(LIMITS.contains(key)){long candidate=value.asLong();if(!target.has(key)||candidate<target.path(key).asLong())target.put(key,candidate);}else if("requireTls".equals(key)){target.put(key,target.path(key).asBoolean(false)||value.asBoolean());}else if("queueOnConcurrency".equals(key)){target.put(key,!target.has(key)?value.asBoolean():target.path(key).asBoolean()&&value.asBoolean());}else if("allowedNetworks".equals(key)||"authenticationTypes".equals(key)){Set<String> incoming=textSet(value);Set<String> current=target.has(key)?textSet(target.get(key)):new HashSet<>(incoming);current.retainAll(incoming);ArrayNode array=target.putArray(key);current.stream().sorted().forEach(array::add);}else target.set(key,value.deepCopy());});}
 private void validate(CreateTrafficPolicyRequest r){if(r==null||r.environment()==null||r.scopeType()==null||!StringUtils.hasText(r.scopeId())||r.policyContent()==null)throw invalid();validateContent(r.policyContent());}
 private void validateContent(JsonNode p){if(!p.isObject())throw invalid();p.fields().forEachRemaining(e->{if(!ALLOWED.contains(e.getKey()))throw invalid();if(LIMITS.contains(e.getKey())&&(!e.getValue().canConvertToLong()||e.getValue().asLong()<=0))throw invalid();if(("allowedNetworks".equals(e.getKey())||"authenticationTypes".equals(e.getKey()))&&!e.getValue().isArray())throw invalid();});}
 private Set<String> textSet(JsonNode a){Set<String>s=new HashSet<>();if(a!=null&&a.isArray())a.forEach(v->s.add(v.asText()));return s;}private void put(Map<PolicyScopeType,String> m,PolicyScopeType t,String v){if(StringUtils.hasText(v))m.put(t,v);}private TrafficPolicyVersion require(String id){TrafficPolicyVersion v=mapper.selectById(id);String t=SecurityContextHolder.getTenantId();if(v==null||(t!=null&&!t.equals(v.getTenantId())))throw new BizException(40490,"OPENAPI_POLICY_NOT_FOUND");return v;}
 private String hash(JsonNode n){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsString(n).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private BizException invalid(){return new BizException(40090,"OPENAPI_TRAFFIC_POLICY_INVALID");}private String targetTenant(String r){String c=SecurityContextHolder.getTenantId();if(c==null)return StringUtils.hasText(r)?r.trim():null;if(StringUtils.hasText(r)&&!c.equals(r.trim()))throw new BizException(40310,"OPENAPI_CROSS_TENANT_ACCESS_DENIED");return c;}
 private void initialize(com.triobase.common.core.entity.BaseEntity e){LocalDateTime n=LocalDateTime.now();e.setCreatedBy(operator());e.setCreatedAt(n);e.setUpdatedBy(operator());e.setUpdatedAt(n);}private void touch(com.triobase.common.core.entity.BaseEntity e){e.setUpdatedBy(operator());e.setUpdatedAt(LocalDateTime.now());}private String operator(){return StringUtils.hasText(SecurityContextHolder.getUserId())?SecurityContextHolder.getUserId():"SYSTEM";}
}
