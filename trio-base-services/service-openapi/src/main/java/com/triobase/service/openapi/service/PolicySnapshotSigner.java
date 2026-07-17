package com.triobase.service.openapi.service;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
@Component
public class PolicySnapshotSigner {
 private final CredentialProvider provider;private final String reference;
 public PolicySnapshotSigner(CredentialProvider provider,@Value("${triobase.openapi.policy.signing-secret-reference}")String reference){this.provider=provider;this.reference=reference;}
 public String sign(String tenant,String environment,long version,String hash){try{String key=provider.resolve(reference).required("signingKey");Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String payload=(tenant==null?"__PLATFORM__":tenant)+":"+environment+":"+version+":"+hash;return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to sign policy snapshot",e);}}
}
