package com.triobase.service.openapi.integration.credential;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class OutboundAuthenticationResolver {

    private final OAuth2TokenProvider tokenProvider;
    private final Clock clock;

    @Autowired
    public OutboundAuthenticationResolver(OAuth2TokenProvider tokenProvider) {
        this(tokenProvider, Clock.systemUTC());
    }

    OutboundAuthenticationResolver(OAuth2TokenProvider tokenProvider, Clock clock) {
        this.tokenProvider = tokenProvider;
        this.clock = clock;
    }

    public OutboundAuthentication resolve(
            AuthenticationType type, CredentialMaterial material, byte[] requestBody) {
        AuthenticationType effectiveType = type == null ? AuthenticationType.NONE : type;
        if (effectiveType == AuthenticationType.NONE) {
            return OutboundAuthentication.none();
        }
        if (material == null) {
            throw new BizException(50031, "OPENAPI_CREDENTIAL_MATERIAL_REQUIRED");
        }
        return switch (effectiveType) {
            case NONE -> OutboundAuthentication.none();
            case API_KEY -> apiKey(material);
            case BASIC -> basic(material);
            case OAUTH2_CLIENT -> bearer(tokenProvider.clientCredentialsToken(material));
            case HMAC -> hmac(material, requestBody);
            case RSA -> rsa(material, requestBody);
            case MTLS -> mtls(material);
        };
    }

    private OutboundAuthentication apiKey(CredentialMaterial material) {
        String name = material.values().getOrDefault("parameterName",
                material.values().getOrDefault("headerName", "X-API-Key"));
        String value = material.required("apiKey");
        if ("query".equalsIgnoreCase(material.values().get("location"))) {
            return new OutboundAuthentication(Map.of(), Map.of(name, value), null);
        }
        return new OutboundAuthentication(Map.of(name, value), Map.of(), null);
    }

    private OutboundAuthentication basic(CredentialMaterial material) {
        String raw = material.required("username") + ":" + material.required("password");
        return new OutboundAuthentication(Map.of("Authorization", "Basic "
                + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8))), Map.of(), null);
    }

    private OutboundAuthentication bearer(String token) {
        return new OutboundAuthentication(Map.of("Authorization", "Bearer " + token), Map.of(), null);
    }

    private OutboundAuthentication hmac(CredentialMaterial material, byte[] body) {
        try {
            String algorithm = material.values().getOrDefault("algorithm", "HmacSHA256");
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(material.required("secret").getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] payload = join(timestamp.getBytes(StandardCharsets.UTF_8), body);
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(payload));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(material.values().getOrDefault("timestampHeader", "X-Signature-Timestamp"), timestamp);
            headers.put(material.values().getOrDefault("signatureHeader", "X-Signature"), signature);
            return new OutboundAuthentication(headers, Map.of(), null);
        } catch (Exception exception) {
            throw new BizException(50032, "OPENAPI_HMAC_PROFILE_INVALID");
        }
    }

    private OutboundAuthentication rsa(CredentialMaterial material, byte[] body) {
        try {
            String algorithm = material.values().getOrDefault("algorithm", "SHA256withRSA");
            byte[] keyBytes = Base64.getDecoder().decode(material.required("privateKeyPkcs8Base64"));
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(key);
            signer.update(body == null ? new byte[0] : body);
            String signature = Base64.getEncoder().encodeToString(signer.sign());
            return new OutboundAuthentication(Map.of(
                    material.values().getOrDefault("signatureHeader", "X-Signature"), signature,
                    "X-Signature-Algorithm", algorithm.toUpperCase(Locale.ROOT)), Map.of(), null);
        } catch (Exception exception) {
            throw new BizException(50032, "OPENAPI_RSA_PROFILE_INVALID");
        }
    }

    private OutboundAuthentication mtls(CredentialMaterial material) {
        return new OutboundAuthentication(Map.of(), Map.of(), material.required("tlsProfileReference"));
    }

    private byte[] join(byte[] first, byte[] second) {
        byte[] safeSecond = second == null ? new byte[0] : second;
        byte[] joined = new byte[first.length + 1 + safeSecond.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        joined[first.length] = (byte) '.';
        System.arraycopy(safeSecond, 0, joined, first.length + 1, safeSecond.length);
        return joined;
    }
}
