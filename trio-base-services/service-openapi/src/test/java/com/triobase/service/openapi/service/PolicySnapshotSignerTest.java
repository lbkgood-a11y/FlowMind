package com.triobase.service.openapi.service;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class PolicySnapshotSignerTest {
 @Test void signsTenantEnvironmentVersionAndHash(){CredentialProvider provider=reference->new CredentialMaterial(Map.of("signingKey","test-signing-key"));PolicySnapshotSigner signer=new PolicySnapshotSigner(provider,"policy-key");String first=signer.sign("tenant-a","PROD",3,"hash");String second=signer.sign("tenant-a","PROD",3,"hash");assertThat(first).isEqualTo(second).isNotBlank();assertThat(first).isNotEqualTo(signer.sign("tenant-a","PROD",4,"hash"));}
}
