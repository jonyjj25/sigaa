package com.sigaa.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void blocksIdentityAtConfiguredThresholdAndReturnsRetryAfter() {
        LoginRateLimitService service = new LoginRateLimitService(900, 3, 20, 30, CLOCK);

        for (int i = 0; i < 3; i++) {
            assertThat(service.checkLogin("10.0.0.1", "USER@Example.com").allowed()).isTrue();
            service.recordLoginFailure("10.0.0.1", "user@example.com");
        }

        LoginRateLimitService.Decision decision = service.checkLogin("10.0.0.2", " user@example.com ");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void successfulLoginClearsIdentityBlockWithoutClearingIpProtection() {
        LoginRateLimitService service = new LoginRateLimitService(900, 2, 10, 30, CLOCK);
        service.recordLoginFailure("10.0.0.1", "user@example.com");
        service.recordLoginFailure("10.0.0.1", "user@example.com");
        assertThat(service.checkLogin("10.0.0.2", "user@example.com").allowed()).isFalse();

        service.recordLoginSuccess("10.0.0.1", "user@example.com");

        assertThat(service.checkLogin("10.0.0.2", "user@example.com").allowed()).isTrue();
    }

    @Test
    void secondFactorUsesAnIndependentBucket() {
        LoginRateLimitService service = new LoginRateLimitService(900, 2, 10, 30, CLOCK);
        service.recordLoginFailure("10.0.0.1", "user@example.com");
        service.recordLoginFailure("10.0.0.1", "user@example.com");

        assertThat(service.checkLogin("10.0.0.2", "user@example.com").allowed()).isFalse();
        assertThat(service.checkSecondFactor("10.0.0.2", "user@example.com").allowed()).isTrue();
    }
}
