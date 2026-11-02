package com.sigaa;

import com.sigaa.model.AllowedOrigin;
import com.sigaa.model.SecurityPolicy;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AllowedOriginRepository;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.SecurityPolicyService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfigurationSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CorsSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityPolicyRepository policies;
    @Autowired AllowedOriginRepository origins;
    @Autowired SecurityPolicyService policyService;
    @Autowired @Qualifier("corsConfigurationSource") CorsConfigurationSource cors;
    @Autowired UsuarioRepository users;
    @Autowired JwtUtil jwt;

    @ParameterizedTest
    @ValueSource(strings = {"https://web.example.test", "http://localhost:4200", "http://localhost:8100",
            "http://localhost", "https://localhost", "capacitor://localhost"})
    void acceptsWebAndMobilePreflightWithoutAuthentication(String origin) throws Exception {
        mvc.perform(options("https://api.example.test/api/auth/me").header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Max-Age", "600"))
                .andExpect(header().string("Access-Control-Allow-Headers", "authorization, content-type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void acceptsOnlyConfiguredHttpMethods(String method) throws Exception {
        mvc.perform(options("/api/security/policies").header("Origin", "https://web.example.test")
                .header("Access-Control-Request-Method", method)).andExpect(status().isOk());
    }

    @Test void rejectsForeignOriginsUnsupportedMethodsAndHeaders() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin", "https://foreign.example.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/auth/me").header("Origin", "https://foreign.example.test"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/me").header("Origin", "https://web.example.test")
                        .header("Access-Control-Request-Method", "TRACE")).andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/me").header("Origin", "https://web.example.test")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Not-Allowed")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://web.example.test", "https://localhost", "capacitor://localhost"})
    void authenticatesTheExistingSessionCookieFromAnAllowedOrigin(String origin) throws Exception {
        Usuario user = new Usuario(); user.setNombre("Persona ficticia"); user.setApellido("CORS");
        user.setCorreo("cors-" + UUID.randomUUID() + "@example.test"); user.setUsername("cors-" + UUID.randomUUID());
        user.setPassword("TEST_ONLY_NOT_REAL"); user = users.saveAndFlush(user);
        String access = jwt.generateToken(user.getCorreo(), "", user.getTokenVersion());
        mvc.perform(get("/api/auth/me").header("Origin", origin).cookie(new Cookie("sigaa_access", access)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(user.getCorreo()))
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    @Test void corsDoesNotBypassAuthenticationAndSameOriginStillWorks() throws Exception {
        mvc.perform(get("/api/auth/me").header("Origin", "https://web.example.test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://web.example.test"));
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test @WithMockUser(roles = "administrador")
    void rejectsWritesToAllHistoricalCorsPoliciesIncludingLegacyAliases() throws Exception {
        for (SecurityPolicy policy : policies.findByCategoria("CORS")) assertPolicyReadOnly(policy);
        for (String alias : List.of("CORS facilita", "Credenciales permitidos", "Origenes permitidos", "Metodos HTTP permitidos")) {
            SecurityPolicy legacy = new SecurityPolicy(); legacy.setNombre(alias); legacy.setValor("test-only");
            legacy.setCategoria("Legado"); assertPolicyReadOnly(policies.saveAndFlush(legacy));
        }
    }

    @Test @WithMockUser(roles = "administrador")
    void rejectsOriginCreationUpdateAndDeletionAndPreservesHistoricalRows() throws Exception {
        AllowedOrigin historical = historicalOrigin();
        long count = origins.count();
        String body = "{\"origen\":\"https://new.example.test\",\"activo\":false}";
        mvc.perform(post("/api/security/allowed-origins").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/security/allowed-origins/{id}", historical.getId()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/security/allowed-origins/{id}", historical.getId())).andExpect(status().isForbidden());
        assertThat(origins.count()).isEqualTo(count);
        AllowedOrigin unchanged = origins.findById(historical.getId()).orElseThrow();
        assertThat(unchanged.getOrigen()).isEqualTo(historical.getOrigen());
        assertThat(unchanged.getActivo()).isTrue();
        mvc.perform(get("/api/security/allowed-origins")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "administrador")
    void historicalDatabaseValuesAndCacheRefreshCannotOverrideDeployment() throws Exception {
        var before = cors.getCorsConfiguration(new MockHttpServletRequest());
        try {
            for (var entry : Map.of("CORS habilitado", "false", "Orígenes permitidos", "https://historical.example.test",
                    "Métodos HTTP permitidos", "TRACE", "Encabezados permitidos", "X-Not-Allowed",
                    "Credenciales permitidas", "false").entrySet()) {
                var policy = policies.findByNombre(entry.getKey()).getFirst();
                policy.setValor(entry.getValue()); policies.saveAndFlush(policy);
            }
            historicalOrigin();
            mvc.perform(post("/api/security/policies/refresh-cache")).andExpect(status().isOk());
            var after = cors.getCorsConfiguration(new MockHttpServletRequest());
            assertThat(after.getAllowedOrigins()).isEqualTo(before.getAllowedOrigins()).doesNotContain("https://historical.example.test");
            assertThat(after.getAllowedMethods()).isEqualTo(before.getAllowedMethods()).doesNotContain("TRACE");
            assertThat(after.getAllowedHeaders()).isEqualTo(before.getAllowedHeaders());
            assertThat(after.getAllowCredentials()).isTrue();
            acceptsWebAndMobilePreflightWithoutAuthentication("https://web.example.test");
            mvc.perform(options("/api/auth/me").header("Origin", "https://historical.example.test")
                    .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
        } finally {
            // La transacción de prueba se revierte; no dejar la caché de otras políticas con datos de la prueba.
            org.springframework.test.context.transaction.TestTransaction.flagForRollback();
            org.springframework.test.context.transaction.TestTransaction.end();
            policyService.refreshCache();
        }
    }

    private void assertPolicyReadOnly(SecurityPolicy policy) throws Exception {
        String original = policy.getValor();
        mvc.perform(put("/api/security/policies/{id}", policy.getId()).contentType("application/json")
                .content("{\"valor\":\"changed\"}")).andExpect(status().isForbidden());
        assertThat(policies.findById(policy.getId()).orElseThrow().getValor()).isEqualTo(original);
    }

    private AllowedOrigin historicalOrigin() {
        AllowedOrigin origin = new AllowedOrigin(); origin.setOrigen("https://historical.example.test");
        return origins.saveAndFlush(origin);
    }
}
