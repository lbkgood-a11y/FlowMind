package com.triobase.service.openapi.integration.credential;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "triobase.openapi.credentials",
        name = "provider",
        havingValue = "local",
        matchIfMissing = true)
public class LocalCredentialProvider implements CredentialProvider {

    private final Environment environment;
    private final ObjectMapper objectMapper;

    public LocalCredentialProvider(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public CredentialMaterial resolve(String secretReference) {
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

    private String normalize(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BizException(40032, "OPENAPI_SECRET_REFERENCE_REQUIRED");
        }
        return reference.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "-");
    }
}
