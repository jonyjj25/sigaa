package com.sigaa.security;

import com.sigaa.service.SecurityPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil(mock(SecurityPolicyService.class));
        Field secret = JwtUtil.class.getDeclaredField("jwtSecret");
        secret.setAccessible(true);
        secret.set(jwtUtil, "test-secret-with-at-least-thirty-two-characters");
        jwtUtil.validateSecret();
    }

    @Test
    void regularAccessTokenDoesNotContainSecondFactorPendingClaim() {
        String token = jwtUtil.generateToken(
                "admin@sigaa.com",
                "administrador",
                Map.of("token_version", 0L),
                60_000L);

        assertThat(jwtUtil.hasClaim(token, "2fa_pending")).isFalse();
        assertThat(jwtUtil.extractLongClaim(token, "token_version")).isZero();
    }

    @Test
    void temporaryTokenContainsSecondFactorPendingClaim() {
        String token = jwtUtil.generateToken(
                "cuenta@institucion.gob.pa",
                "Miembro",
                Map.of("2fa_pending", "true", "token_version", 2L),
                60_000L);

        assertThat(jwtUtil.hasClaim(token, "2fa_pending")).isTrue();
        assertThat(jwtUtil.extractLongClaim(token, "token_version")).isEqualTo(2L);
    }

    @Test
    void malformedTokenNeverReportsAClaim() {
        assertThat(jwtUtil.hasClaim("token-invalido", "2fa_pending")).isFalse();
    }
}
