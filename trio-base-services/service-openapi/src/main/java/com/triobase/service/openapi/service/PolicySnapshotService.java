package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.PolicyEnforcementState;
import com.triobase.service.openapi.domain.entity.PolicySnapshot;
import com.triobase.service.openapi.domain.entity.TrafficPolicyVersion;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.infrastructure.mapper.PolicyEnforcementStateMapper;
import com.triobase.service.openapi.infrastructure.mapper.PolicySnapshotMapper;
import com.triobase.service.openapi.infrastructure.mapper.TrafficPolicyVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
@Service @RequiredArgsConstructor
public class PolicySnapshotService {
 private static final List<String> POINTS=List.of("platform-gateway","service-openapi-runtime");
 private final PolicySnapshotMapper snapshotMapper;private final TrafficPolicyVersionMapper policyMapper;
 private final PolicyEnforcementStateMapper enforcementMapper;private final PolicySnapshotSigner signer;
 private final PolicySnapshotPublisher publisher;private final ObjectMapper objectMapper;private final IntegrationAuditService auditService;
 @Transactional public PolicySnapshot publish(String requestedTenant,Environment environment){String tenant=targetTenant(requestedTenant);snapshotMapper.lockSeries((tenant==null?"__PLATFORM__":tenant)+":"+environment);long version=snapshotMapper.nextVersion(tenant,environment.name());List<TrafficPolicyVersion> policies=policyMapper.selectList(new LambdaQueryWrapper<TrafficPolicyVersion>().eq(TrafficPolicyVersion::getEnvironment,environment).eq(TrafficPolicyVersion::getLifecycleState,VersionLifecycleState.PUBLISHED)).stream().filter(p->java.util.Objects.equals(tenant,p.getTenantId())).sorted(Comparator.comparing((TrafficPolicyVersion p)->p.getScopeType().name()).thenComparing(TrafficPolicyVersion::getScopeId).thenComparing(TrafficPolicyVersion::getPolicyVersion)).toList();ArrayNode items=objectMapper.createArrayNode();policies.forEach(p->items.add(objectMapper.createObjectNode().put("policyId",p.getId()).put("scopeType",p.getScopeType().name()).put("scopeId",p.getScopeId()).put("policyVersion",p.getPolicyVersion()).put("contentHash",p.getContentHash()).set("content",p.getPolicyContent())));ObjectNode content=objectMapper.createObjectNode();content.put("tenantId",tenant==null?"__PLATFORM__":tenant);content.put("environment",environment.name());content.put("snapshotVersion",version);content.set("policies",items);String hash=hash(content);PolicySnapshot snapshot=new PolicySnapshot();snapshot.setId(UlidGenerator.nextUlid());snapshot.setTenantId(tenant);snapshot.setEnvironment(environment);snapshot.setSnapshotVersion(version);snapshot.setPolicyContent(content);snapshot.setContentHash(hash);snapshot.setSignature(signer.sign(tenant,environment.name(),version,hash));snapshot.setPublishedBy(operator());snapshot.setPublishedAt(LocalDateTime.now());snapshotMapper.insert(snapshot);String enforcementTenant=tenant==null?"__PLATFORM__":tenant;POINTS.forEach(point->enforcementMapper.requireVersion(point,enforcementTenant,environment.name(),version));boolean distributed=publisher.publish(snapshot);auditService.success("ACCESS_POLICY_SNAPSHOT_PUBLISHED","POLICY_SNAPSHOT",snapshot.getId(),objectMapper.createObjectNode().put("snapshotVersion",version).put("distributed",distributed));return snapshot;}
 @Transactional public PolicyEnforcementState reportApplied(String point,String requestedTenant,Environment environment,long version){if(!POINTS.contains(point)||version<0)throw new BizException(40091,"OPENAPI_POLICY_REPORT_INVALID");String tenant=targetTenant(requestedTenant);String stored=tenant==null?"__PLATFORM__":tenant;if(enforcementMapper.reportApplied(point,stored,environment.name(),version)!=1)throw new BizException(40491,"OPENAPI_POLICY_ENFORCEMENT_STATE_NOT_FOUND");return enforcementMapper.find(point,stored,environment.name());}
 public PolicyEnforcementState status(String point,String requestedTenant,Environment environment){String tenant=targetTenant(requestedTenant);PolicyEnforcementState state=enforcementMapper.find(point,tenant==null?"__PLATFORM__":tenant,environment.name());if(state==null)throw new BizException(40491,"OPENAPI_POLICY_ENFORCEMENT_STATE_NOT_FOUND");return state;}
 public void requireCurrent(String point,String requestedTenant,Environment environment){PolicyEnforcementState s=status(point,requestedTenant,environment);if(!"CURRENT".equals(s.getDriftState())||s.getAppliedPolicyVersion()<s.getRequiredPolicyVersion())throw new BizException(50391,"OPENAPI_POLICY_ENFORCEMENT_LAG_FAIL_CLOSED");}
 private String hash(ObjectNode node){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private String targetTenant(String r){String c=SecurityContextHolder.getTenantId();if(c==null)return StringUtils.hasText(r)?r.trim():null;if(StringUtils.hasText(r)&&!c.equals(r.trim()))throw new BizException(40310,"OPENAPI_CROSS_TENANT_ACCESS_DENIED");return c;}private String operator(){return StringUtils.hasText(SecurityContextHolder.getUserId())?SecurityContextHolder.getUserId():"SYSTEM";}
}
