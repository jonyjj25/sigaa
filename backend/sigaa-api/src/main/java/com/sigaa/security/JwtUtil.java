package com.sigaa.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.sigaa.service.SecurityPolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final SecurityPolicyService policyService;

    public JwtUtil(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
    }

    public String generateToken(String username, String roles) {
        return generateToken(username, roles, null, policyService.getJwtExpirationMs());
    }

    public String generateToken(String username, String roles, long tokenVersion) {
        return generateToken(username, roles, Map.of("token_version", tokenVersion), policyService.getJwtExpirationMs());
    }

    public String generateToken(String username, String roles, Map<String, Object> extraClaims, long expirationMs) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret.getBytes());
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMs);

        var builder = JWT.create()
                .withSubject(username)
                .withArrayClaim("roles", roles != null ? roles.split(",") : new String[0])
                .withIssuedAt(now)
                .withExpiresAt(expiresAt);

        if (extraClaims != null) {
            for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String v) builder.withClaim(key, v);
                else if (value instanceof Boolean v) builder.withClaim(key, v);
                else if (value instanceof Integer v) builder.withClaim(key, v);
                else if (value instanceof Long v) builder.withClaim(key, v);
            }
        }

        return builder.sign(algorithm);
    }

    public String extractUsername(String token) {
        DecodedJWT jwt = getVerifier().verify(token);
        return jwt.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            getVerifier().verify(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean hasClaim(String token, String claimName) {
        try {
            DecodedJWT jwt = getVerifier().verify(token);
            if (!jwt.getClaims().containsKey(claimName)) return false;
            var claim = jwt.getClaim(claimName);
            return claim != null && !claim.isNull();
        } catch (Exception ex) {
            return false;
        }
    }

    public long extractLongClaim(String token, String claimName) {
        Long value = getVerifier().verify(token).getClaim(claimName).asLong();
        return value == null ? 0L : value;
    }

    private JWTVerifier getVerifier() {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret.getBytes());
        return JWT.require(algorithm).build();
    }
}
