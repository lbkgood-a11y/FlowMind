package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import com.triobase.service.openapi.domain.entity.*;
import com.triobase.service.openapi.domain.enums.*;
import com.triobase.service.openapi.dto.EffectiveTrafficPolicy;
import com.triobase.service.openapi.infrastructure.mapper.ApiProductVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CredentialBindingMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class IntegrationAdmissionServiceTest {
 @Mock ApplicationClientMapper clientMapper;@Mock CredentialBindingMapper bindingMapper;@Mock ApiProductVersionMapper productVersionMapper;@Mock CredentialProvider provider;@Mock ProductSubscriptionService subscriptions;@Mock TrafficPolicyService policies;@Mock PolicySnapshotService snapshots;@Mock StringRedisTemplate redis;@Mock ValueOperations<String,String> values;@Mock IntegrationAuditService audit;@Mock PolicyChangeNotifier notifier;@Mock CallbackProfileService callbacks;ObjectMapper json=new ObjectMapper();
 @Test void admitsAuthenticatedSubscribedClientUnderEffectiveLimits(){IntegrationAdmissionService service=service();ApplicationClient client=client(0);when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client);CredentialBinding binding=binding();when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));when(provider.resolve("vault:client-1")).thenReturn(new CredentialMaterial(Map.of("apiKey","secret")));ProductSubscription subscription=new ProductSubscription();subscription.setId("sub-1");subscription.setApiProductVersionId("pv1");when(subscriptions.requireActiveAccess(any(),any(),any(),any(),any())).thenReturn(subscription);ApiProductVersion product=new ApiProductVersion();product.setApiProductId("product-1");when(productVersionMapper.selectById("pv1")).thenReturn(product);when(policies.resolve(any(),any(),any(),any(),any(),any(),any())).thenReturn(new EffectiveTrafficPolicy("tenant-a",Environment.PROD,"client-1","product-1","orders.get","GET",json.createObjectNode().put("ratePerSecond",10).put("dailyQuota",100).put("maxBodyBytes",1024),List.of()));PolicyEnforcementState state=new PolicyEnforcementState();state.setAppliedPolicyVersion(7L);when(snapshots.status("platform-gateway","tenant-a",Environment.PROD)).thenReturn(state);when(redis.opsForValue()).thenReturn(values);when(values.increment(any())).thenReturn(1L);var decision=service.admit(request("secret"));assertThat(decision.allowed()).isTrue();assertThat(decision.applicationClientId()).isEqualTo("client-1");assertThat(decision.policyVersion()).isEqualTo(7L);}
 @Test void authenticationFailuresAreCountedAndCanAutomaticallySuspend(){IntegrationAdmissionService service=service();ApplicationClient client=client(9);when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client);when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding()));when(provider.resolve("vault:client-1")).thenReturn(new CredentialMaterial(Map.of("apiKey","correct")));var decision=service.admit(request("wrong"));assertThat(decision.allowed()).isFalse();assertThat(client.getLifecycleState()).isEqualTo(ApplicationLifecycleState.SUSPENDED);verify(notifier).publishAfterCommit("tenant-a",Environment.PROD);}
 @Test void admitsPinnedCallbackClientWithoutRouteSubscription(){IntegrationAdmissionService service=service();ApplicationClient client=client(0);when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client);CallbackProfile profile=new CallbackProfile();profile.setTenantId("tenant-a");CallbackProfileVersion version=new CallbackProfileVersion();version.setId("callback-v1");version.setApplicationClientId("client-1");version.setMaxBodyBytes(1024L);version.setSecurityPolicy(json.createObjectNode().put("requireTls",true));when(callbacks.resolvePublished("cb_key","tenant-a",Environment.PROD)).thenReturn(new CallbackProfileService.PublishedCallback(profile,version));PolicyEnforcementState state=new PolicyEnforcementState();state.setAppliedPolicyVersion(8L);when(snapshots.status("platform-gateway","tenant-a",Environment.PROD)).thenReturn(state);IntegrationAdmissionRequest callback=new IntegrationAdmissionRequest("tenant-a","key","PROD","cb_key","POST",null,"8.8.8.8",20,true,Set.of(),null,null,null,null,null,true);var decision=service.admit(callback);assertThat(decision.allowed()).isTrue();assertThat(decision.subscriptionId()).isEqualTo("CALLBACK:callback-v1");}
 @Test void suspendedApplicationCannotSubmitCallback(){IntegrationAdmissionService service=service();ApplicationClient client=client(0);client.setLifecycleState(ApplicationLifecycleState.SUSPENDED);when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client);IntegrationAdmissionRequest callback=new IntegrationAdmissionRequest("tenant-a","key","PROD","cb_key","POST",null,"8.8.8.8",20,true,Set.of(),null,null,null,null,null,true);assertThat(service.admit(callback).allowed()).isFalse();}
 private IntegrationAdmissionService service(){return new IntegrationAdmissionService(clientMapper,bindingMapper,productVersionMapper,provider,subscriptions,policies,snapshots,new NetworkPolicyMatcher(),redis,audit,notifier,callbacks);}private ApplicationClient client(int count){ApplicationClient c=new ApplicationClient();c.setId("client-1");c.setTenantId("tenant-a");c.setEnvironment(Environment.PROD);c.setClientKey("key");c.setLifecycleState(ApplicationLifecycleState.ACTIVE);c.setSecurityViolationCount(count);return c;}private CredentialBinding binding(){CredentialBinding b=new CredentialBinding();b.setApplicationClientId("client-1");b.setAuthenticationType(AuthenticationType.API_KEY);b.setSecretReference("vault:client-1");b.setLifecycleState(CredentialBindingState.ACTIVE);b.setValidFrom(LocalDateTime.now().minusDays(1));return b;}private IntegrationAdmissionRequest request(String credential){return new IntegrationAdmissionRequest("tenant-a","key","PROD","orders.get","GET",credential,"8.8.8.8",10,true,Set.of("orders.read"),null,null,null,null,null);}
}
