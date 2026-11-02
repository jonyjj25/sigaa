package com.sigaa.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class CorsPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class BindingConfiguration {}

    @Test void bindsOnlyDeploymentPropertiesAndPreservesCredentialConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("app.cors.allowed-origins=https://web.example.test,capacitor://localhost",
                        "app.cors.allowed-methods=GET,POST,OPTIONS", "app.cors.allowed-headers=Content-Type,Authorization",
                        "app.cors.exposed-headers=Content-Disposition", "app.cors.allow-credentials=true",
                        "app.cors.max-age=120", "app.cors-allowed-origins=https://obsolete.example.test")
                .run(context -> {
                    var configuration = context.getBean(CorsProperties.class).toConfiguration();
                    assertThat(configuration.getAllowedOrigins()).containsExactly("https://web.example.test", "capacitor://localhost");
                    assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "OPTIONS");
                    assertThat(configuration.getAllowedHeaders()).containsExactly("Content-Type", "Authorization");
                    assertThat(configuration.getExposedHeaders()).containsExactly("Content-Disposition");
                    assertThat(configuration.getAllowCredentials()).isTrue();
                    assertThat(configuration.getMaxAge()).isEqualTo(120L);
                });
    }

    @Test void allowsDisablingCredentialsAndLeavesUnconfiguredOriginsClosed() {
        var closed = properties(List.of(), false, 0).toConfiguration();
        assertThat(closed.checkOrigin("https://unconfigured.example.test")).isNull();
        assertThat(closed.getAllowCredentials()).isFalse();
    }

    @Test void rejectsWildcardTogetherWithCredentialsBeforeServingRequests() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of("*"), true, 600).toConfiguration());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "not-an-origin", "https://web.example.test/path", "https://*.example.test",
            "https://web.example.test/", "https://web.example.test?query=1", "https://user@web.example.test",
            "file://localhost", "https://web.example.test#fragment"})
    void rejectsInvalidOrAmbiguousOrigins(String origin) {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of(origin), true, 600).toConfiguration());
    }

    @Test void rejectsNegativePreflightAgeAndUnnecessaryHttpMethods() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of(), true, -1).toConfiguration());
        assertThatIllegalArgumentException().isThrownBy(() -> new CorsProperties(List.of(), List.of("TRACE"),
                List.of(), List.of(), true, 600).toConfiguration());
    }

    private CorsProperties properties(List<String> origins, boolean credentials, long maxAge) {
        return new CorsProperties(origins, List.of("GET", "POST", "OPTIONS"),
                List.of("Authorization", "Content-Type"), List.of("Content-Disposition"), credentials, maxAge);
    }
}
