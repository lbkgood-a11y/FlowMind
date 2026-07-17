package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.CallbackProfile;
import com.triobase.service.openapi.domain.entity.CallbackProfileVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CallbackProfileVersionMutationRequest;
import com.triobase.service.openapi.dto.CreateCallbackProfileRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackProfileMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackProfileVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallbackProfileServiceTest {

    @Mock private CallbackProfileMapper profileMapper;
    @Mock private CallbackProfileVersionMapper versionMapper;
    @Mock private ApplicationClientMapper clientMapper;
    @Mock private StructureVersionMapper structureMapper;
    @Mock private MappingVersionMapper mappingMapper;
    @Mock private CredentialProvider credentialProvider;
    @Mock private IntegrationAuditService auditService;
    private CallbackProfileService service;

    @BeforeEach
    void setUp() {
        service = new CallbackProfileService(profileMapper, versionMapper, clientMapper,
                structureMapper, mappingMapper, credentialProvider, auditService, new ObjectMapper());
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "owner-1", "Owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsOpaqueVersionedProfileAndPublishesPinnedSecurityContract() {
        ApplicationClient client = new ApplicationClient();
        client.setId("client-1");
        client.setTenantId("tenant-a");
        client.setEnvironment(Environment.PROD);
        client.setLifecycleState(ApplicationLifecycleState.ACTIVE);
        when(clientMapper.selectById("client-1")).thenReturn(client);
        StructureVersion structure = new StructureVersion();
        structure.setLifecycleState(VersionLifecycleState.PUBLISHED);
        when(structureMapper.selectById("structure-v1")).thenReturn(structure);
        ArgumentCaptor<CallbackProfile> profileCaptor = ArgumentCaptor.forClass(CallbackProfile.class);
        ArgumentCaptor<CallbackProfileVersion> versionCaptor =
                ArgumentCaptor.forClass(CallbackProfileVersion.class);
        when(profileMapper.insert(profileCaptor.capture())).thenReturn(1);
        when(versionMapper.insert(versionCaptor.capture())).thenReturn(1);

        var created = service.create(new CreateCallbackProfileRequest(
                null, "Partner result", "owner-1", mutation()));

        assertThat(created.callbackKey()).startsWith("cb_").hasSizeGreaterThan(30);
        assertThat(created.validationResult().path("valid").asBoolean()).isTrue();
        CallbackProfile profile = profileCaptor.getValue();
        CallbackProfileVersion version = versionCaptor.getValue();
        when(profileMapper.selectById(profile.getId())).thenReturn(profile);
        when(versionMapper.selectById(version.getId())).thenReturn(version);
        when(versionMapper.updateById(any(CallbackProfileVersion.class))).thenReturn(1);
        when(credentialProvider.resolve("vault:callback"))
                .thenReturn(new CredentialMaterial(Map.of("secret", "hidden")));

        var published = service.publish(version.getId());

        assertThat(published.lifecycleState()).isEqualTo(VersionLifecycleState.PUBLISHED);
        assertThat(published.authenticationType()).isEqualTo(AuthenticationType.HMAC);
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("CALLBACK_PROFILE_CREATED"),
                org.mockito.ArgumentMatchers.eq("CALLBACK_PROFILE"), any(), any());
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("CALLBACK_PROFILE_PUBLISHED"),
                org.mockito.ArgumentMatchers.eq("CALLBACK_PROFILE_VERSION"), any(), any());
    }

    private CallbackProfileVersionMutationRequest mutation() {
        return new CallbackProfileVersionMutationRequest(Environment.PROD, "client-1",
                AuthenticationType.HMAC, "vault:callback", "structure-v1", null,
                "/eventId", "/executionId", CallbackCorrelationType.EXECUTION_ID,
                "partner-result", 300, 1024, 60, 202,
                "application/json", "{\"accepted\":true}",
                new ObjectMapper().createObjectNode().put("requireTls", true));
    }
}
