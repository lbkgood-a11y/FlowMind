package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(prefix = "triobase.openapi.credentials", name = "provider", havingValue = "vault")
public class VaultCredentialProvider implements CredentialProvider {

    private final RestClient restClient;
    private final String vaultToken;
    private final SecureRandom secureRandom = new SecureRandom();

    public VaultCredentialProvider(
            RestClient.Builder builder,
            @Value("${triobase.openapi.credentials.vault.address}") String address,
            @Value("${triobase.openapi.credentials.vault.token:}") String vaultToken) {
        this.restClient = builder.baseUrl(address).build();
        this.vaultToken = vaultToken;
    }

    @Override
    public CredentialMaterial resolve(String secretReference) {
        validateReference(secretReference);
        if (!StringUtils.hasText(vaultToken)) {
            throw new BizException(50330, "OPENAPI_VAULT_AUTHENTICATION_UNAVAILABLE");
        }
        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/v1/" + secretReference)
                    .header("X-Vault-Token", vaultToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception exception) {
            throw new BizException(50331, "OPENAPI_VAULT_SECRET_RESOLUTION_FAILED");
        }
        JsonNode values = response == null ? null : response.path("data");
        if (values != null && values.has("data")) {
            values = values.path("data");
        }
        if (values == null || !values.isObject()) {
            throw new BizException(40431, "OPENAPI_VAULT_SECRET_NOT_FOUND");
        }
        Map<String, String> material = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = values.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            material.put(field.getKey(), field.getValue().asText());
        }
        return new CredentialMaterial(material);
    }

    @Override
    public ProvisionedCredential provision(String secretReference, AuthenticationType type) {
        validateReference(secretReference);
        if (!StringUtils.hasText(vaultToken)) {
            throw new BizException(50330, "OPENAPI_VAULT_AUTHENTICATION_UNAVAILABLE");
        }
        CredentialMaterial material = generate(type);
        try {
            restClient.post().uri("/v1/" + secretReference).header("X-Vault-Token", vaultToken)
                    .body(Map.of("data", material.values())).retrieve().toBodilessEntity();
            return new ProvisionedCredential(secretReference, material);
        } catch (Exception exception) {
            throw new BizException(50331, "OPENAPI_VAULT_SECRET_PROVISION_FAILED");
        }
    }

    @Override
    public void revoke(String secretReference) {
        validateReference(secretReference);
        try {
            restClient.delete().uri("/v1/" + secretReference).header("X-Vault-Token", vaultToken)
                    .retrieve().toBodilessEntity();
        } catch (Exception exception) {
            throw new BizException(50331, "OPENAPI_VAULT_SECRET_REVOCATION_FAILED");
        }
    }

    private void validateReference(String secretReference) {
        if (!StringUtils.hasText(secretReference) || secretReference.contains("..")
                || secretReference.startsWith("http:") || secretReference.startsWith("https:")) {
            throw new BizException(40032, "OPENAPI_VAULT_SECRET_REFERENCE_INVALID");
        }
    }

    private CredentialMaterial generate(AuthenticationType type) {
        if (type == null || type == AuthenticationType.NONE || type == AuthenticationType.MTLS
                || type == AuthenticationType.RSA) {
            throw new BizException(40052, "OPENAPI_CREDENTIAL_TYPE_REQUIRES_IMPORT");
        }
        return switch (type) {
            case API_KEY -> new CredentialMaterial(Map.of("apiKey", random(32)));
            case BASIC -> new CredentialMaterial(Map.of("username", "client-" + random(8), "password", random(32)));
            case OAUTH2_CLIENT -> new CredentialMaterial(Map.of("clientId", "client-" + random(8), "clientSecret", random(32)));
            case HMAC -> new CredentialMaterial(Map.of("secret", random(48), "algorithm", "HmacSHA256"));
            default -> throw new BizException(40052, "OPENAPI_CREDENTIAL_TYPE_REQUIRES_IMPORT");
        };
    }

    private String random(int bytes) {
        byte[] value = new byte[bytes]; secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
