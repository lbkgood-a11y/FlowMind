package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCredentialProviderTest {

    @Test
    void resolvesConfiguredDevelopmentSecretByReference() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("triobase.openapi.credentials.local.secrets.partner-api-v1",
                        "{\"apiKey\":\"development-only\"}");
        LocalCredentialProvider provider = new LocalCredentialProvider(environment, new ObjectMapper());

        CredentialMaterial material = provider.resolve("partner/api/v1");

        assertThat(material.required("apiKey")).isEqualTo("development-only");
    }

    @Test
    void missingReferenceDoesNotRevealConfiguration() {
        LocalCredentialProvider provider = new LocalCredentialProvider(new MockEnvironment(), new ObjectMapper());
        assertThatThrownBy(() -> provider.resolve("missing"))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_LOCAL_CREDENTIAL_NOT_FOUND")
                .hasMessageNotContaining("development-only");
    }
}
