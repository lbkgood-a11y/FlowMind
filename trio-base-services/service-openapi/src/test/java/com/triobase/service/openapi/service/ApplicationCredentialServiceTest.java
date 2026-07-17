package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.CredentialBinding;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CredentialBindingState;
import com.triobase.service.openapi.dto.RotateCredentialRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CredentialBindingMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class ApplicationCredentialServiceTest {
 @Mock CredentialBindingMapper bindingMapper;@Mock ApplicationClientMapper clientMapper;@Mock CredentialProvider provider;@Mock IntegrationAuditService audit;@Mock PolicyChangeNotifier notifier;ApplicationCredentialService service;
 @BeforeEach void setUp(){service=new ApplicationCredentialService(bindingMapper,clientMapper,provider,audit,notifier);SecurityContextHolder.set(new SecurityContextHolder.SecurityContext("user","owner","tenant-a",List.of(),List.of(),1L,1L,1L));}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void rotationReturnsSecretOnceAndMovesOldBindingToOverlap(){ApplicationClient client=new ApplicationClient();client.setId("client-1");client.setTenantId("tenant-a");client.setLifecycleState(ApplicationLifecycleState.ACTIVE);when(clientMapper.selectById("client-1")).thenReturn(client);CredentialBinding old=new CredentialBinding();old.setId("old");old.setApplicationClientId("client-1");old.setCredentialVersion(1);old.setLifecycleState(CredentialBindingState.ACTIVE);when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old));when(bindingMapper.selectOne(any(Wrapper.class))).thenReturn(old);when(provider.provision("vault:client/v2",AuthenticationType.API_KEY)).thenReturn(new CredentialProvider.ProvisionedCredential("vault:client/v2",new CredentialMaterial(Map.of("apiKey","one-time-secret"))));var response=service.rotateGenerated("client-1",new RotateCredentialRequest(AuthenticationType.API_KEY,"vault:client/v2",300,null));assertThat(response.credentialVersion()).isEqualTo(2);assertThat(response.oneTimeSecret()).containsEntry("apiKey","one-time-secret");assertThat(old.getLifecycleState()).isEqualTo(CredentialBindingState.RETIRING);assertThat(old.getRetirementAt()).isNotNull();ArgumentCaptor<CredentialBinding> inserted=ArgumentCaptor.forClass(CredentialBinding.class);verify(bindingMapper).insert(inserted.capture());assertThat(inserted.getValue().getSecretReference()).isEqualTo("vault:client/v2");}
 @Test void revocationCallsCredentialProviderAndPublishesFailClosedPolicy(){ApplicationClient client=new ApplicationClient();client.setId("client-1");client.setTenantId("tenant-a");client.setEnvironment(com.triobase.service.openapi.domain.enums.Environment.PROD);client.setLifecycleState(ApplicationLifecycleState.ACTIVE);CredentialBinding binding=new CredentialBinding();binding.setId("binding-1");binding.setApplicationClientId("client-1");binding.setSecretReference("vault:client/v1");binding.setLifecycleState(CredentialBindingState.ACTIVE);when(bindingMapper.selectById("binding-1")).thenReturn(binding);when(clientMapper.selectById("client-1")).thenReturn(client);var result=service.revoke("binding-1");assertThat(result.lifecycleState()).isEqualTo(CredentialBindingState.REVOKED);verify(provider).revoke("vault:client/v1");verify(notifier).publishAfterCommit("tenant-a",com.triobase.service.openapi.domain.enums.Environment.PROD);}
}
