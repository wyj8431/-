package com.example.lowcode.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenService {
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtTokenService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.signingKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.accessTokenTtl = accessTokenTtl;
    }

    public IssuedToken issue(long userId, long tenantId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(Long.toString(userId))
            .claim("tenantId", tenantId)
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJwt.sign(new MACSigner(signingKey.getEncoded()));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
        return new IssuedToken(signedJwt.serialize(), accessTokenTtl.toSeconds());
    }

    public SecretKey signingKey() {
        return signingKey;
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
