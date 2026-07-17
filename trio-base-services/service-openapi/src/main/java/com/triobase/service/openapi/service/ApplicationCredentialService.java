package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.CredentialBinding;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CredentialBindingState;
import com.triobase.service.openapi.dto.CreateCredentialBindingRequest;
import com.triobase.service.openapi.dto.CredentialBindingResponse;
import com.triobase.service.openapi.dto.RotateCredentialRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CredentialBindingMapper;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class ApplicationCredentialService {
 private final CredentialBindingMapper bindingMapper; private final ApplicationClientMapper clientMapper;
 private final CredentialProvider credentialProvider; private final IntegrationAuditService auditService; private final PolicyChangeNotifier policyNotifier;
 @Transactional public CredentialBindingResponse bindImported(String clientId,CreateCredentialBindingRequest r){ApplicationClient c=requireClient(clientId);validateType(r==null?null:r.authenticationType());if(r==null||!StringUtils.hasText(r.secretReference())||invalidWindow(r.validFrom(),r.expiresAt()))throw invalid();credentialProvider.resolve(r.secretReference());CredentialBinding b=newBinding(c,r.authenticationType(),r.secretReference(),r.validFrom(),r.expiresAt());bindingMapper.insert(b);audit("APPLICATION_CREDENTIAL_BOUND",b);return response(b,Map.of());}
 @Transactional public CredentialBindingResponse rotateGenerated(String clientId,RotateCredentialRequest r){ApplicationClient c=requireClient(clientId);validateType(r==null?null:r.authenticationType());if(r==null||!StringUtils.hasText(r.newSecretReference())||r.overlapSeconds()<0||r.overlapSeconds()>2592000||invalidWindow(LocalDateTime.now(),r.expiresAt()))throw invalid();CredentialProvider.ProvisionedCredential provisioned=credentialProvider.provision(r.newSecretReference(),r.authenticationType());LocalDateTime retirement=LocalDateTime.now().plusSeconds(r.overlapSeconds());List<CredentialBinding> active=bindingMapper.selectList(new LambdaQueryWrapper<CredentialBinding>().eq(CredentialBinding::getApplicationClientId,clientId).eq(CredentialBinding::getLifecycleState,CredentialBindingState.ACTIVE));for(CredentialBinding old:active){old.setLifecycleState(CredentialBindingState.RETIRING);old.setRetirementAt(retirement);touch(old);bindingMapper.updateById(old);}CredentialBinding b=newBinding(c,r.authenticationType(),provisioned.secretReference(),LocalDateTime.now(),r.expiresAt());b.setOneTimeSecretDelivered(true);bindingMapper.insert(b);audit("APPLICATION_CREDENTIAL_ROTATED",b);return response(b,provisioned.oneTimeMaterial().values());}
 @Transactional public CredentialBindingResponse revoke(String bindingId){CredentialBinding b=requireBinding(bindingId);if(b.getLifecycleState()==CredentialBindingState.REVOKED)return response(b,Map.of());ApplicationClient c=requireClient(b.getApplicationClientId());credentialProvider.revoke(b.getSecretReference());b.setLifecycleState(CredentialBindingState.REVOKED);b.setRevokedAt(LocalDateTime.now());touch(b);bindingMapper.updateById(b);audit("APPLICATION_CREDENTIAL_REVOKED",b);policyNotifier.publishAfterCommit(c.getTenantId(),c.getEnvironment());return response(b,Map.of());}
 @Transactional public int retireDue(){List<CredentialBinding> due=bindingMapper.selectList(new LambdaQueryWrapper<CredentialBinding>().eq(CredentialBinding::getLifecycleState,CredentialBindingState.RETIRING).le(CredentialBinding::getRetirementAt,LocalDateTime.now()));for(CredentialBinding b:due){b.setLifecycleState(CredentialBindingState.RETIRED);touch(b);bindingMapper.updateById(b);}return due.size();}
 @Transactional public int expireDue(){List<CredentialBinding> due=bindingMapper.selectList(new LambdaQueryWrapper<CredentialBinding>().in(CredentialBinding::getLifecycleState,CredentialBindingState.ACTIVE,CredentialBindingState.RETIRING).le(CredentialBinding::getExpiresAt,LocalDateTime.now()));for(CredentialBinding b:due){b.setLifecycleState(CredentialBindingState.EXPIRED);touch(b);bindingMapper.updateById(b);}return due.size();}
 public List<CredentialBindingResponse> list(String clientId){requireClient(clientId);return bindingMapper.selectList(new LambdaQueryWrapper<CredentialBinding>().eq(CredentialBinding::getApplicationClientId,clientId).orderByDesc(CredentialBinding::getCredentialVersion)).stream().map(b->response(b,Map.of())).toList();}
 public CredentialBindingResponse plaintext(String bindingId){requireBinding(bindingId);throw new BizException(40351,"OPENAPI_STORED_CREDENTIAL_PLAINTEXT_FORBIDDEN_ROTATE_INSTEAD");}
 private CredentialBinding newBinding(ApplicationClient c,AuthenticationType type,String ref,LocalDateTime from,LocalDateTime expires){CredentialBinding latest=bindingMapper.selectOne(new LambdaQueryWrapper<CredentialBinding>().eq(CredentialBinding::getApplicationClientId,c.getId()).orderByDesc(CredentialBinding::getCredentialVersion).last("LIMIT 1"));CredentialBinding b=new CredentialBinding();b.setId(UlidGenerator.nextUlid());b.setApplicationClientId(c.getId());b.setAuthenticationType(type);b.setCredentialVersion(latest==null?1:latest.getCredentialVersion()+1);b.setSecretReference(ref);b.setLifecycleState(CredentialBindingState.ACTIVE);b.setValidFrom(from==null?LocalDateTime.now():from);b.setExpiresAt(expires);b.setOneTimeSecretDelivered(false);initialize(b);return b;}
 private ApplicationClient requireClient(String id){ApplicationClient c=clientMapper.selectById(id);String t=SecurityContextHolder.getTenantId();if(c==null||(t!=null&&!t.equals(c.getTenantId()))||c.getLifecycleState()!=ApplicationLifecycleState.ACTIVE)throw new BizException(40471,"OPENAPI_ACTIVE_APPLICATION_CLIENT_NOT_FOUND");return c;} private CredentialBinding requireBinding(String id){CredentialBinding b=bindingMapper.selectById(id);if(b==null)throw new BizException(40472,"OPENAPI_CREDENTIAL_BINDING_NOT_FOUND");requireClient(b.getApplicationClientId());return b;}
 private void validateType(AuthenticationType t){if(t==null||t==AuthenticationType.NONE)throw invalid();}private boolean invalidWindow(LocalDateTime from,LocalDateTime until){return from!=null&&until!=null&&!until.isAfter(from);}private BizException invalid(){return new BizException(40072,"OPENAPI_CREDENTIAL_BINDING_INVALID");}
 private CredentialBindingResponse response(CredentialBinding b,Map<String,String> once){return new CredentialBindingResponse(b.getId(),b.getApplicationClientId(),b.getAuthenticationType(),b.getCredentialVersion(),b.getSecretReference(),b.getLifecycleState(),b.getValidFrom(),b.getExpiresAt(),b.getRetirementAt(),once);}
 private void audit(String event,CredentialBinding b){auditService.success(event,"CREDENTIAL_BINDING",b.getId(),JsonNodeFactory.instance.objectNode().put("clientId",b.getApplicationClientId()).put("credentialVersion",b.getCredentialVersion()));}
 private void initialize(com.triobase.common.core.entity.BaseEntity e){LocalDateTime n=LocalDateTime.now();e.setCreatedBy(operator());e.setCreatedAt(n);e.setUpdatedBy(operator());e.setUpdatedAt(n);if(e instanceof com.triobase.service.openapi.domain.model.VersionedEntity v)v.setRowVersion(0L);}private void touch(com.triobase.common.core.entity.BaseEntity e){e.setUpdatedBy(operator());e.setUpdatedAt(LocalDateTime.now());}private String operator(){return StringUtils.hasText(SecurityContextHolder.getUserId())?SecurityContextHolder.getUserId():"SYSTEM";}
}
