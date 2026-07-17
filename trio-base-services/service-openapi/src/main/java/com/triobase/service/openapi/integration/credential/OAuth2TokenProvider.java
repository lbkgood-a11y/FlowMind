package com.triobase.service.openapi.integration.credential;

public interface OAuth2TokenProvider {
    String clientCredentialsToken(CredentialMaterial material);
}
