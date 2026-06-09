package com.sigaa.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:changeit}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    public String generateToken(String username, String roles) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret.getBytes());
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + jwtExpirationMs);

        return JWT.create()
                .withSubject(username)
                .withArrayClaim("roles", roles != null ? roles.split(",") : new String[0])
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
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

    private JWTVerifier getVerifier() {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret.getBytes());
        return JWT.require(algorithm).build();
    }
}
