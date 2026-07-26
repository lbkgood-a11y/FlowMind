package com.triobase.common.openapi.credential;

public interface OAuth2TokenProvider {

    String clientCredentialsToken(CredentialMaterial material);
}
