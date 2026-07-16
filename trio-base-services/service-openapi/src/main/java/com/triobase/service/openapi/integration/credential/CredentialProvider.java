package com.triobase.service.openapi.integration.credential;

public interface CredentialProvider {
    CredentialMaterial resolve(String secretReference);
}
