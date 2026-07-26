package com.triobase.common.openapi.credential;

import com.triobase.common.openapi.enums.AuthenticationType;

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
