package com.sigaa.service;

import com.sigaa.config.SecurityDeploymentProperties;
import com.sigaa.model.SecurityPolicy;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.security.JwtUtil;
import com.sigaa.security.UploadPolicyFilter;
import jakarta.servlet.MultipartConfigElement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicySeparationTest {
    private static final long MB = 1024L * 1024L;

    @Test void databaseAndCacheRefreshCannotOverrideTechnicalLifetimesOrNetwork() {
        var backend = new SecurityDeploymentProperties(90_000, true, true, List.of("192.0.2.10"), List.of("192.0.2.11"));
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Tiempo de expiración JWT (min)", "999", "JWT"),
                policy("Tiempo de expiración del token temporal 2FA (seg)", "999", "Autenticación"),
                policy("Restricción por IP", "false", "IP"), policy("Lista blanca de IP activa", "false", "IP"),
                policy("Autenticación de dos factores", "true", "Autenticación")));
        var service = new SecurityPolicyService(repository, backend, multipart(10, 50), 240_000);
        for (int i = 0; i < 2; i++) {
            service.refreshCache();
            assertThat(service.getJwtExpirationMs()).isEqualTo(240_000);
            assertThat(service.getTwoFactorTokenExpiryMs()).isEqualTo(90_000);
            assertThat(service.isIpRestrictionEnabled()).isTrue();
            assertThat(service.isIpAllowed("192.0.2.10")).isTrue();
            assertThat(service.isIpAllowed("192.0.2.11")).isFalse();
            assertThat(service.isIpAllowed("192.0.2.12")).isFalse();
            assertThat(service.isTwoFactorEnabled()).isTrue();
        }
        JwtUtil jwt = new JwtUtil(service);
        ReflectionTestUtils.setField(jwt, "jwtSecret", "TEST_ONLY_security_separation_1234567890");
        var decoded = com.auth0.jwt.JWT.decode(jwt.generateToken("session@example.test", "Miembro", 0));
        assertThat(decoded.getExpiresAt().getTime() - decoded.getIssuedAt().getTime()).isEqualTo(240_000);
    }

    @Test void functionalPoliciesAndLegacyAliasesRemainEffective() {
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Longitud de contraseña mínima", "12", "Contrasena"),
                policy("Tiempo bloqueo (min)", "7", "Acceso"), policy("Intentos fallidos máximos", "4", "Acceso"),
                policy("Restricción por horario", "true", "IP"), policy("Horario inicio acceso", "08:00", "IP")));
        var service = service(repository, 10, 50);
        service.refreshCache();
        assertThat(service.getPasswordMinLength()).isEqualTo(12);
        assertThat(service.getLockoutDurationMs()).isEqualTo(7 * 60_000);
        assertThat(service.getMaxLoginAttempts()).isEqualTo(4);
        assertThat(service.isScheduleRestrictionEnabled()).isTrue();
        assertThat(service.getAccessStartTime().toString()).isEqualTo("08:00");
        assertThat(service.isAdministrable(policy("Parámetro desconocido", "true", "Acceso"))).isFalse();
        assertThat(service.isAdministrable(policy("Restricción por horario", "true", "CORS"))).isFalse();
    }

    @Test void historicalOversizedFileLimitsAreCappedWithoutChangingTheStoredValue() {
        var repository = mock(SecurityPolicyRepository.class);
        var historical = policy("Tamaño máximo de archivos (MB)", "999999999999", "Carga");
        when(repository.findAll()).thenReturn(List.of(historical, policy("Límite de carga por petición (MB)", "9999", "Carga")));
        var service = service(repository, 20, 7);
        service.refreshCache();
        assertThat(service.getServerMaxFileSizeMB()).isEqualTo(7);
        assertThat(service.getMaxFileSizeMB()).isEqualTo(7);
        assertThat(service.getMaxFileSizeBytes()).isEqualTo(7 * MB);
        assertThat(service.getMaxRequestSizeBytes()).isEqualTo(7 * MB);
        assertThat(historical.getValor()).isEqualTo("999999999999");
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateFunctionalFileLimit(historical, "8"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateFunctionalFileLimit(historical, "-1"));
        service.validateFunctionalFileLimit(historical, "4");
        verify(repository, never()).save(any());
    }

    @Test void legacyAliasesCannotOverrideAnExistingCanonicalFunctionalPolicy() {
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Longitud mínima de contraseña", "12", "Contrasena"),
                policy("Longitud de contraseña mínima", "4", "Contrasena"),
                policy("Tiempo de bloqueo (min)", "30", "Acceso"), policy("Tiempo bloqueo (min)", "1", "Acceso")));
        var service = service(repository, 10, 50); service.refreshCache();
        assertThat(service.getPasswordMinLength()).isEqualTo(12);
        assertThat(service.getLockoutDurationMs()).isEqualTo(30 * 60_000);
    }

    @Test void passwordMinimumBoundaryRemainsUsableByTheConfiguredBcryptEncoder() {
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Longitud mínima de contraseña", "72", "Contrasena")));
        var service = service(repository, 10, 50); service.refreshCache();
        String password = "Aa1!" + "x".repeat(68);
        assertThat(new SecurityPasswordService(service).isPasswordValid(password)).isTrue();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        assertThatCode(() -> encoder.encode(password)).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(password + "x"));
    }

    @Test void uploadFilterAppliesFunctionalAndAbsoluteServerLimits() throws Exception {
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Tamaño máximo de archivos (MB)", "1", "Carga")));
        var service = service(repository, 10, 5); service.refreshCache();
        UploadPolicyFilter filter = new UploadPolicyFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setContentType("multipart/form-data");
        request.addPart(new MockPart("file", "fixture.pdf", "%PDF-1.7".getBytes()) {
            @Override public long getSize() { return MB + 1; }
        });
        var response = new MockHttpServletResponse(); var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(400); assertThat(called).isFalse();
        var oversizedRequest = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return 5 * MB + 1; }
        };
        oversizedRequest.setContentType("multipart/form-data");
        response = new MockHttpServletResponse();
        filter.doFilter(oversizedRequest, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(400); assertThat(called).isFalse();
    }

    @Test void migrationRequiresAnExplicitNetworkDecisionIfTheHistoricalRestrictionWasActive() {
        var repository = mock(SecurityPolicyRepository.class);
        when(repository.findAll()).thenReturn(List.of(policy("Restricción por IP", "true", "IP")));
        var service = new SecurityPolicyService(repository,
                new SecurityDeploymentProperties(300_000, null, false, List.of(), List.of()), multipart(10, 50), 3_600_000);
        assertThatIllegalStateException().isThrownBy(service::init)
                .withMessageContaining("app.security.ip-restriction-enabled");
    }

    @Test void rejectsUnlimitedServletSizesAndInvalidTechnicalLifetimesAtStartup() {
        var repository = mock(SecurityPolicyRepository.class);
        var backend = new SecurityDeploymentProperties(300_000, false, false, List.of(), List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> new SecurityPolicyService(repository, backend,
                new MultipartConfigElement("", -1, 50 * MB, 0), 3_600_000).init());
        assertThatIllegalArgumentException().isThrownBy(() -> new SecurityPolicyService(repository, backend, multipart(10, 50), 0).init());
    }

    @Test void rejectsFileSizeAboveRequestSizeAndNonPositiveTechnicalSizesAtStartup() {
        var repository = mock(SecurityPolicyRepository.class);
        var backend = new SecurityDeploymentProperties(300_000, false, false, List.of(), List.of());
        for (var limits : List.of(multipart(20, 7), multipart(0, 50), multipart(10, 0), multipart(10, -1))) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new SecurityPolicyService(repository, backend, limits, 3_600_000).init())
                    .withMessageContaining("multipart");
        }
    }

    @Test void rejectsSubsecondExtremeJwtAndTemporaryTokenLongerThanSessionAtStartup() {
        var repository = mock(SecurityPolicyRepository.class);
        var backend = new SecurityDeploymentProperties(300_000, false, false, List.of(), List.of());
        for (long duration : new long[]{-1, 0, 999, 86_400_001L, Long.MAX_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new SecurityPolicyService(repository, backend, multipart(10, 50), duration).init())
                    .withMessageContaining("jwt.expiration-ms");
        }
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SecurityPolicyService(repository, backend, multipart(10, 50), 60_000).init())
                .withMessageContaining("two-factor-token-expiry-ms").withMessageContaining("jwt.expiration-ms");
    }

    private SecurityPolicyService service(SecurityPolicyRepository repository, int file, int request) {
        return new SecurityPolicyService(repository, new SecurityDeploymentProperties(300_000, false, false,
                List.of(), List.of()), multipart(file, request), 3_600_000);
    }

    private MultipartConfigElement multipart(int file, int request) {
        return new MultipartConfigElement("", file * MB, request * MB, 0);
    }

    private SecurityPolicy policy(String name, String value, String category) {
        var policy = new SecurityPolicy(); policy.setNombre(name); policy.setValor(value); policy.setCategoria(category); return policy;
    }
}
