package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ConnectorEndpoint;
import com.triobase.service.openapi.domain.entity.ConnectorVersion;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.ConnectorOperationClass;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.ConnectorVersionMutationRequest;
import com.triobase.service.openapi.dto.CreateConnectorRequest;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorEndpointMapper;
import com.triobase.service.openapi.infrastructure.mapper.ConnectorVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorRegistryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ConnectorEndpointMapper endpointMapper;
    @Mock private ConnectorVersionMapper versionMapper;
    @Mock private OutboundTargetPolicy targetPolicy;
    @Mock private IntegrationAuditService auditService;
    private ConnectorRegistryService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorRegistryService(endpointMapper, versionMapper, targetPolicy, auditService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsTenantScopedDraftAndStoresOnlySecretReference() {
        when(endpointMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        var response = service.create(createRequest());

        assertThat(response.tenantId()).isEqualTo("tenant-a");
        assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);
        assertThat(response.secretReference()).isEqualTo("vault:secret/data/partner");
        ArgumentCaptor<ConnectorVersion> version = ArgumentCaptor.forClass(ConnectorVersion.class);
        verify(versionMapper).insert(version.capture());
        assertThat(version.getValue().getSecretReference()).isEqualTo("vault:secret/data/partner");
        verify(targetPolicy).validate("https://partner.example", createRequest().networkPolicy());
    }

    @Test
    void createsNextDraftOnlyAfterTenantAndLifecycleChecks() {
        ConnectorEndpoint endpoint = endpoint();
        ConnectorVersion latest = version(VersionLifecycleState.PUBLISHED);
        latest.setVersionNumber(2);
        when(endpointMapper.selectById("connector-1")).thenReturn(endpoint);
        when(versionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(latest);

        var response = service.createDraft("connector-1", mutation());

        assertThat(response.versionNumber()).isEqualTo(3);
        assertThat(response.lifecycleState()).isEqualTo(VersionLifecycleState.DRAFT);
    }

    @Test
    void publishedVersionsAreImmutable() {
        ConnectorVersion version = version(VersionLifecycleState.PUBLISHED);
        when(versionMapper.selectById("version-1")).thenReturn(version);
        when(endpointMapper.selectById("connector-1")).thenReturn(endpoint());

        assertThatThrownBy(() -> service.updateDraft("version-1", mutation()))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_PUBLISHED_CONNECTOR_IMMUTABLE");
    }

    private CreateConnectorRequest createRequest() {
        var networkPolicy = objectMapper.createObjectNode();
        networkPolicy.putArray("allowedHosts").add("partner.example");
        return new CreateConnectorRequest(null, "partner-order", "Partner order", "team-a",
                "https://partner.example", "/orders", "POST", 10_000,
                ConnectorOperationClass.STATE_CHANGING, AuthenticationType.API_KEY,
                "vault:secret/data/partner", networkPolicy,
                1024 * 1024);
    }

    private ConnectorVersionMutationRequest mutation() {
        return new ConnectorVersionMutationRequest("https://partner.example", "/orders", "POST", 10_000,
                ConnectorOperationClass.STATE_CHANGING, AuthenticationType.API_KEY,
                "vault:secret/data/partner", objectMapper.createObjectNode(), 1024 * 1024);
    }

    private ConnectorEndpoint endpoint() {
        ConnectorEndpoint endpoint = new ConnectorEndpoint();
        endpoint.setId("connector-1");
        endpoint.setTenantId("tenant-a");
        endpoint.setConnectorKey("partner-order");
        endpoint.setDisplayName("Partner order");
        endpoint.setLifecycleState(AssetLifecycleState.ACTIVE);
        return endpoint;
    }

    private ConnectorVersion version(VersionLifecycleState state) {
        ConnectorVersion version = new ConnectorVersion();
        version.setId("version-1");
        version.setConnectorEndpointId("connector-1");
        version.setVersionNumber(1);
        version.setLifecycleState(state);
        version.setBaseUrl("https://partner.example");
        version.setOperationPath("/orders");
        version.setHttpMethod("POST");
        version.setTimeoutMillis(10_000);
        version.setOperationClass(ConnectorOperationClass.STATE_CHANGING);
        version.setAuthenticationType(AuthenticationType.API_KEY);
        version.setSecretReference("vault:secret/data/partner");
        version.setNetworkPolicy(objectMapper.createObjectNode());
        version.setResponseSizeLimit(1024L);
        return version;
    }
}
