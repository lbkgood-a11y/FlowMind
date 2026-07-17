package com.triobase.service.openapi.integration.credential;

import com.triobase.service.openapi.domain.enums.AuthenticationType;

public interface CredentialProvider {
    CredentialMaterial resolve(String secretReference);

    default ProvisionedCredential provision(String secretReference, AuthenticationType type) {
        throw new UnsupportedOperationException("Credential provisioning is not supported by this provider");
    }

    default void revoke(String secretReference) {
        throw new UnsupportedOperationException("Credential revocation is not supported by this provider");
    }

    record ProvisionedCredential(String secretReference, CredentialMaterial oneTimeMaterial) { }
}
