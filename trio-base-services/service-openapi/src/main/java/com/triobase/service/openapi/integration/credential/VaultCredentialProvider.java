package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "triobase.openapi.credentials", name = "provider", havingValue = "vault")
public class VaultCredentialProvider implements CredentialProvider {

    private final RestClient restClient;
    private final String vaultToken;

    public VaultCredentialProvider(
            RestClient.Builder builder,
            @Value("${triobase.openapi.credentials.vault.address}") String address,
            @Value("${triobase.openapi.credentials.vault.token:}") String vaultToken) {
        this.restClient = builder.baseUrl(address).build();
        this.vaultToken = vaultToken;
    }

    @Override
    public CredentialMaterial resolve(String secretReference) {
        if (!StringUtils.hasText(secretReference) || secretReference.contains("..")
                || secretReference.startsWith("http:" ) || secretReference.startsWith("https:")) {
            throw new BizException(40032, "OPENAPI_VAULT_SECRET_REFERENCE_INVALID");
        }
        if (!StringUtils.hasText(vaultToken)) {
            throw new BizException(50330, "OPENAPI_VAULT_AUTHENTICATION_UNAVAILABLE");
        }
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(URI.create("/v1/" + secretReference))
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
}
