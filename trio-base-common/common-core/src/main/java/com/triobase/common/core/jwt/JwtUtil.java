package com.triobase.common.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.triobase.common.core.id.UlidGenerator;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 工具类 — Nimbus JOSE + JWT，HS256 对称签名。
 * 签发逻辑由 service-auth 调用；验证逻辑由 gateway/service-auth 共用。
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    public static String createAccessToken(String userId, String username, List<String> roles,
                                           String secret, int ttlSeconds) {
        return createToken(userId, username, roles, "access", secret, ttlSeconds);
    }

    public static String createRefreshToken(String userId, String username,
                                            String secret, int ttlSeconds) {
        return createToken(userId, username, null, "refresh", secret, ttlSeconds);
    }

    private static String createToken(String userId, String username, List<String> roles,
                                      String type, String secret, int ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("triobase")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .jwtID(UlidGenerator.nextUlid())
                    .claim("username", username)
                    .claim("type", type);

            if (roles != null && !roles.isEmpty()) {
                builder.claim("roles", roles);
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    builder.build()
            );
            jwt.sign(new MACSigner(secret.getBytes()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("JWT 签发失败", e);
        }
    }

    public static JwtPayload verifyAndParse(String token, String secret) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret.getBytes());
            if (!jwt.verify(verifier)) {
                return JwtPayload.invalid("签名验证失败");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                return JwtPayload.invalid("Token 已过期");
            }
            return JwtPayload.valid(claims, jwt.getJWTClaimsSet().getJWTID());
        } catch (ParseException | JOSEException e) {
            return JwtPayload.invalid("Token 解析失败: " + e.getMessage());
        }
    }

    public record JwtPayload(boolean valid, String error,
                             String userId, String username, String type,
                             String jti, Date expirationTime, List<String> roles) {

        static JwtPayload valid(JWTClaimsSet claims, String jti) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            return new JwtPayload(true, null,
                    claims.getSubject(),
                    (String) claims.getClaim("username"),
                    (String) claims.getClaim("type"),
                    jti,
                    claims.getExpirationTime(),
                    roles != null ? roles : List.of());
        }

        static JwtPayload invalid(String error) {
            return new JwtPayload(false, error, null, null, null, null, null, List.of());
        }

        @SuppressWarnings("unchecked")
        public static JwtPayload fromClaimsMap(Map<String, Object> claims) {
            Object exp = claims.get("exp");
            Date expTime = exp instanceof Date ? (Date) exp : null;
            return new JwtPayload(true, null,
                    (String) claims.get("sub"),
                    (String) claims.get("username"),
                    (String) claims.get("type"),
                    (String) claims.get("jti"),
                    expTime,
                    (List<String>) claims.getOrDefault("roles", List.of()));
        }
    }
}
