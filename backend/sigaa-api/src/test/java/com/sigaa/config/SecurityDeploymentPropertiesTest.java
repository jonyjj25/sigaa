package com.sigaa.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class SecurityDeploymentPropertiesTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityDeploymentProperties.class)
    static class BindingConfiguration {}

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, 999, 300001, Long.MAX_VALUE})
    void rejectsInvalidTemporaryTokenLifetimesDuringBinding(long expiry) {
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("app.security.two-factor-token-expiry-ms=" + expiry)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(longs = {1000, 90000, 300000})
    void acceptsTemporaryTokenLifetimeBoundaries(long expiry) {
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("app.security.two-factor-token-expiry-ms=" + expiry)
                .run(context -> assertThat(context.getBean(SecurityDeploymentProperties.class).twoFactorTokenExpiryMs()).isEqualTo(expiry));
    }

    @Test void bindsTechnicalSettingsFromTheDeployment() {
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class).withPropertyValues(
                "app.security.two-factor-token-expiry-ms=90000", "app.security.ip-restriction-enabled=true",
                "app.security.ip-whitelist-active=true", "app.security.ip-whitelist=192.0.2.10-192.0.2.20,192.0.2.30",
                "app.security.ip-blacklist=192.0.2.15").run(context -> {
            var properties = context.getBean(SecurityDeploymentProperties.class);
            assertThat(properties.twoFactorTokenExpiryMs()).isEqualTo(90_000);
            assertThat(properties.isIpAllowed("192.0.2.10")).isTrue();
            assertThat(properties.isIpAllowed("192.0.2.20")).isTrue();
            assertThat(properties.isIpAllowed("192.0.2.30")).isTrue();
            assertThat(properties.isIpAllowed("192.0.2.15")).isFalse();
            assertThat(properties.isIpAllowed("192.0.2.31")).isFalse();
        });
    }

    @Test void anEmptyActiveWhitelistDeniesAccessAndDisablingTheRestrictionIsExplicit() {
        assertThat(new SecurityDeploymentProperties(300_000, true, true, List.of(), List.of())
                .isIpAllowed("192.0.2.1")).isFalse();
        assertThat(new SecurityDeploymentProperties(300_000, false, true, List.of(), List.of("192.0.2.1"))
                .isIpAllowed("192.0.2.1")).isTrue();
    }

    @Test void anAbsentNetworkSettingIsDistinguishableForTheMigrationGuard() {
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("app.security.ip-restriction-enabled=").run(context -> {
                    assertThat(context.getBean(SecurityDeploymentProperties.class).ipRestrictionEnabled()).isNull();
                    assertThat(context.getBean(SecurityDeploymentProperties.class).twoFactorTokenExpiryMs()).isEqualTo(300_000);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"999.1.1.1", "192.0.2.1-", "192.0.2.20-192.0.2.10", "192.0.2.0/24", "host.example.test"})
    void invalidNetworkRulesFailBeforeTheApplicationServesRequests(String rule) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SecurityDeploymentProperties(300_000, true, true, List.of(rule), List.of()));
    }
}
