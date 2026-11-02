package com.sigaa;

import com.sigaa.model.IpRestriction;
import com.sigaa.model.SecurityPolicy;
import com.sigaa.repository.IpRestrictionRepository;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.service.SecurityPolicyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "administrador")
class SecuritySettingsSeparationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityPolicyRepository policies;
    @Autowired IpRestrictionRepository ips;
    @Autowired SecurityPolicyService service;

    @AfterEach void rollbackAndRefresh() {
        if (TestTransaction.isActive()) { TestTransaction.flagForRollback(); TestTransaction.end(); }
        service.refreshCache();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tiempo de expiración JWT (min)", "Tiempo expiración JWT (min)",
            "Tiempo de expiración del token temporal 2FA (seg)", "Tiempo de vencimiento del token temporal 2FA (seg)",
            "Límite de carga por petición (MB)", "Restricción por IP", "Lista blanca de IP activa",
            "Lista negra de IP activa", "Restricción por rango de IP", "Restricción por ubicación",
            "Renovación automática del token", "Tiempo máximo de inactividad (min)",
            "Duración máxima de sesión (min)", "Parámetro técnico desconocido"})
    void administratorCannotModifyTechnicalOrUnknownPoliciesEvenWithAnEditableFlagInTheBody(String name) throws Exception {
        SecurityPolicy historical = new SecurityPolicy(); historical.setNombre(name); historical.setCategoria("Legado");
        historical.setValor("7"); historical = policies.saveAndFlush(historical);
        mvc.perform(get("/api/security/policies/{id}", historical.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.administrable").value(false)).andExpect(jsonPath("$.valor").value("7"));
        mvc.perform(put("/api/security/policies/{id}", historical.getId()).contentType("application/json")
                        .content("{\"valor\":\"999\",\"administrable\":true}"))
                .andExpect(status().isForbidden());
        assertThat(policies.findById(historical.getId()).orElseThrow().getValor()).isEqualTo("7");
    }

    @Test void functionalPoliciesRemainAdministrableAndKeepTheirExistingValidation() throws Exception {
        update("Intentos fallidos máximos", "4");
        assertThat(service.getMaxLoginAttempts()).isEqualTo(4);
        update("Autenticación de dos factores", "true");
        assertThat(service.isTwoFactorEnabled()).isTrue();
        update("Horario inicio acceso", "08:00");
        assertThat(service.getAccessStartTime().toString()).isEqualTo("08:00");
        update("Longitud mínima de contraseña", "12");
        assertThat(service.getPasswordMinLength()).isEqualTo(12);
    }

    @Test void fileLimitCannotExceedTheServerAndLegacyOversizedValuesAreOnlyCappedNotRewritten() throws Exception {
        SecurityPolicy file = named("Tamaño máximo de archivos (MB)");
        file.setValor("1000000"); policies.saveAndFlush(file); service.refreshCache();
        mvc.perform(get("/api/security/policies/{id}", file.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.administrable").value(true))
                .andExpect(jsonPath("$.maximoServidorMb").value(10)).andExpect(jsonPath("$.valor").value("10"));
        assertThat(policies.findById(file.getId()).orElseThrow().getValor()).isEqualTo("1000000");
        mvc.perform(put("/api/security/policies/{id}", file.getId()).contentType("application/json")
                .content("{\"valor\":\"11\"}")).andExpect(status().isBadRequest());
        assertThat(policies.findById(file.getId()).orElseThrow().getValor()).isEqualTo("1000000");
        update("Tamaño máximo de archivos (MB)", "4");
        assertThat(service.getMaxFileSizeBytes()).isEqualTo(4L * 1024 * 1024);
    }

    @Test void historicalNetworkRulesAreReadOnlyAndNoLongerConfigureAccess() throws Exception {
        IpRestriction historical = new IpRestriction(); historical.setTipo("BLACKLIST");
        historical.setDireccionIp("192.0.2.10"); historical = ips.saveAndFlush(historical);
        long count = ips.count();
        String body = "{\"tipo\":\"WHITELIST\",\"direccionIp\":\"192.0.2.20\",\"activo\":false}";
        mvc.perform(post("/api/security/ip-restrictions").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/security/ip-restrictions/{id}", historical.getId()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/security/ip-restrictions/{id}", historical.getId())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/security/ip-restrictions/{id}/toggle", historical.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/security/ip-restrictions")).andExpect(status().isOk());
        assertThat(ips.count()).isEqualTo(count);
        assertThat(ips.findById(historical.getId()).orElseThrow().getDireccionIp()).isEqualTo("192.0.2.10");
        assertThat(ips.findById(historical.getId()).orElseThrow().getActivo()).isTrue();
        var networkPolicy = named("Restricción por IP"); networkPolicy.setValor("true"); policies.saveAndFlush(networkPolicy);
        mvc.perform(post("/api/security/policies/refresh-cache")).andExpect(status().isOk());
        assertThat(service.isIpRestrictionEnabled()).isFalse();
        assertThat(service.isIpAllowed("192.0.2.10")).isTrue();
    }

    @Test void databaseValuesCannotOverrideLifetimesOrAbsoluteRequestSizeAfterCacheRefresh() throws Exception {
        for (String name : new String[]{"Tiempo de expiración JWT (min)", "Tiempo de expiración del token temporal 2FA (seg)",
                "Límite de carga por petición (MB)"}) {
            var policy = named(name); policy.setValor("9999999"); policies.saveAndFlush(policy);
        }
        mvc.perform(post("/api/security/policies/refresh-cache")).andExpect(status().isOk());
        assertThat(service.getJwtExpirationMs()).isEqualTo(3_600_000);
        assertThat(service.getTwoFactorTokenExpiryMs()).isEqualTo(300_000);
        assertThat(service.getMaxRequestSizeBytes()).isEqualTo(50L * 1024 * 1024);
    }

    @Test @WithAnonymousUser void anonymousCannotChangeFunctionalOrTechnicalPolicies() throws Exception { rejectWrites(401); }
    @Test @WithMockUser(roles = "Miembro") void memberCannotChangeFunctionalOrTechnicalPolicies() throws Exception { rejectWrites(403); }

    private void rejectWrites(int status) throws Exception {
        for (String name : new String[]{"Intentos fallidos máximos", "Tiempo de expiración JWT (min)"}) {
            mvc.perform(put("/api/security/policies/{id}", named(name).getId()).contentType("application/json")
                    .content("{\"valor\":\"4\"}")).andExpect(status().is(status));
        }
    }

    private void update(String name, String value) throws Exception {
        var policy = named(name);
        mvc.perform(put("/api/security/policies/{id}", policy.getId()).contentType("application/json")
                .content("{\"valor\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(value))
                .andExpect(jsonPath("$.administrable").value(true));
        assertThat(policies.findById(policy.getId()).orElseThrow().getValor()).isEqualTo(value);
    }

    private SecurityPolicy named(String name) { return policies.findByNombre(name).getFirst(); }
}
