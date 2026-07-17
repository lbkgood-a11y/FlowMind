package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "triobase.openapi.credentials",
        name = "provider",
        havingValue = "local",
        matchIfMissing = true)
public class LocalCredentialProvider implements CredentialProvider {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Map<String, CredentialMaterial> generated = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public LocalCredentialProvider(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public CredentialMaterial resolve(String secretReference) {
        CredentialMaterial material = generated.get(secretReference);
        if (material != null) {
            return material;
        }
        String property = "triobase.openapi.credentials.local.secrets." + normalize(secretReference);
        String json = environment.getProperty(property);
        if (json == null || json.isBlank()) {
            throw new BizException(40431, "OPENAPI_LOCAL_CREDENTIAL_NOT_FOUND");
        }
        try {
            Map<String, String> values = objectMapper.readValue(json, new TypeReference<>() { });
            return new CredentialMaterial(values);
        } catch (Exception exception) {
            throw new BizException(40032, "OPENAPI_LOCAL_CREDENTIAL_INVALID");
        }
    }

    @Override
    public ProvisionedCredential provision(String secretReference, AuthenticationType type) {
        if (generated.containsKey(secretReference)) {
            throw new BizException(40951, "OPENAPI_CREDENTIAL_REFERENCE_EXISTS");
        }
        CredentialMaterial material = generate(type);
        generated.put(secretReference, material);
        return new ProvisionedCredential(secretReference, material);
    }

    @Override
    public void revoke(String secretReference) {
        generated.remove(secretReference);
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
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BizException(40032, "OPENAPI_SECRET_REFERENCE_REQUIRED");
        }
        return reference.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "-");
    }
}
