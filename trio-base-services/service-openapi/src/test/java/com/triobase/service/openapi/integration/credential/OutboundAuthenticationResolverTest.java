package com.triobase.service.openapi.integration.credential;

import com.triobase.service.openapi.domain.enums.AuthenticationType;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundAuthenticationResolverTest {

    private final OutboundAuthenticationResolver resolver = new OutboundAuthenticationResolver(
            material -> "oauth-token",
            Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void supportsNoneApiKeyBasicOAuthAndMtlsProfiles() {
        assertThat(resolver.resolve(AuthenticationType.NONE, null, new byte[0]).headers()).isEmpty();

        var apiKey = resolver.resolve(AuthenticationType.API_KEY,
                new CredentialMaterial(Map.of("apiKey", "secret", "headerName", "X-Partner-Key")), new byte[0]);
        assertThat(apiKey.headers()).containsEntry("X-Partner-Key", "secret");

        var queryKey = resolver.resolve(AuthenticationType.API_KEY,
                new CredentialMaterial(Map.of("apiKey", "secret", "location", "query", "parameterName", "key")),
                new byte[0]);
        assertThat(queryKey.queryParameters()).containsEntry("key", "secret");

        var basic = resolver.resolve(AuthenticationType.BASIC,
                new CredentialMaterial(Map.of("username", "alice", "password", "pwd")), new byte[0]);
        assertThat(basic.headers().get("Authorization")).startsWith("Basic ");

        var oauth = resolver.resolve(AuthenticationType.OAUTH2_CLIENT,
                new CredentialMaterial(Map.of("clientId", "id")), new byte[0]);
        assertThat(oauth.headers()).containsEntry("Authorization", "Bearer oauth-token");

        var mtls = resolver.resolve(AuthenticationType.MTLS,
                new CredentialMaterial(Map.of("tlsProfileReference", "vault:tls/partner-v1")), new byte[0]);
        assertThat(mtls.tlsProfileReference()).isEqualTo("vault:tls/partner-v1");
    }

    @Test
    void createsHmacAndRsaSignaturesWithoutReturningPrivateMaterial() throws Exception {
        var hmac = resolver.resolve(AuthenticationType.HMAC,
                new CredentialMaterial(Map.of("secret", "signing-secret")), "body".getBytes());
        assertThat(hmac.headers()).containsKeys("X-Signature", "X-Signature-Timestamp");
        assertThat(hmac.headers().values()).doesNotContain("signing-secret");

        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        var rsa = resolver.resolve(AuthenticationType.RSA,
                new CredentialMaterial(Map.of("privateKeyPkcs8Base64", privateKey)), "body".getBytes());
        assertThat(rsa.headers()).containsKeys("X-Signature", "X-Signature-Algorithm");
        assertThat(rsa.headers().values()).doesNotContain(privateKey);
    }
}
