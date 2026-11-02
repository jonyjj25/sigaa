package com.sigaa;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;
import com.sigaa.repository.UbicacionRepository;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.repository.BackupRepository;
import com.sigaa.model.Backup;
import com.sigaa.model.SecurityPolicy;
import com.sigaa.service.SecurityPolicyService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfigurationAuthorizationIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UbicacionRepository ubicacionRepository;

    @Autowired
    private SecurityPolicyRepository securityPolicyRepository;

    @Autowired
    private BackupRepository backupRepository;

    @Autowired
    private SecurityPolicyService securityPolicyService;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios", "/api/roles", "/api/permisos", "/api/modulos",
            "/api/parametros", "/api/security/policies", "/api/audit-logs",
            "/api/notificaciones/canales", "/api/backups", "/api/backups/status", "/api/organizacion",
            "/api/horarios", "/api/ubicaciones",
            // One boundary per protected module.  Spring Security runs before
            // controller dispatch, so this prevents an unprotected module from
            // being accidentally introduced even when its first action is POST.
            "/api/actas", "/api/asistencia", "/api/dashboard", "/api/documentos",
            "/api/grupos", "/api/inventario", "/api/notificaciones",
            "/api/notificaciones/configuracion", "/api/notificaciones/eventos",
            "/api/organizations", "/api/catalogos", "/api/reuniones",
            "/api/reportes", "/api/tareas", "/api/unidades",
            "/api/config/preferences"
    })
    void configurationEndpointsRejectUnauthenticatedRequests(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/horarios", "/api/ubicaciones", "/api/security/policies", "/api/roles", "/api/modulos", "/api/backups/status"})
    @WithMockUser(roles = "Miembro")
    void memberCannotReadAdministrativeConfiguration(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/horarios", "/api/ubicaciones", "/api/security/policies", "/api/roles", "/api/modulos", "/api/backups/status"})
    @WithMockUser(roles = "Jefe de Unidad")
    void unitChiefCannotReadAdministrativeConfiguration(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/horarios", "/api/ubicaciones", "/api/security/policies", "/api/roles", "/api/modulos", "/api/backups/status"})
    @WithMockUser(roles = "administrador")
    void administratorCanReadAdministrativeConfiguration(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    @Transactional
    @WithMockUser(roles = "administrador")
    void backupResponsesExposeOperationalEvidenceWithoutInternalPaths() throws Exception {
        Backup backup = new Backup();
        backup.setNombre("evidencia-prueba.dump.enc");
        backup.setTamanio("1.0 KB");
        backup.setEstado("completado");
        backup.setTipo("manual");
        backup.setRutaArchivo("C:/servidor/ruta-interna/evidencia-prueba.dump.enc");
        backup.setCopiaExterna(true);
        backup.setChecksumSha256("a".repeat(64));
        backupRepository.saveAndFlush(backup);

        mvc.perform(get("/api/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("evidencia-prueba.dump.enc"))
                .andExpect(jsonPath("$[0].copiaExterna").value(true))
                .andExpect(jsonPath("$[0].checksumSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$[0].rutaArchivo").doesNotExist());

        mvc.perform(get("/api/backups/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cifradoConfigurado").isBoolean())
                .andExpect(jsonPath("$.totalRespaldos").isNumber())
                .andExpect(jsonPath("$.encryptionKey").doesNotExist())
                .andExpect(jsonPath("$.directorioExterno").doesNotExist());
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "administrador")
    void permissionCatalogIsReadOnlyAtRuntime() throws Exception {
        String permission = "{\"modulo\":\"Actas\",\"accion\":\"Ver\",\"recurso\":\"*\"}";

        mvc.perform(post("/api/permisos").contentType("application/json").content(permission))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/permisos/1").contentType("application/json").content(permission))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/permisos/1/toggle"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/permisos/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @org.junit.jupiter.api.Test
    @Transactional
    @WithMockUser(roles = "administrador")
    void administratorCanCreateAValidLocationAndItPersistsInTheTestDatabase() throws Exception {
        String name = "Ubicacion de prueba integrada";
        mvc.perform(post("/api/ubicaciones")
                        .contentType("application/json")
                        .content("{\"nombre\":\"" + name + "\",\"direccion\":\"Prueba\",\"latitud\":8.98,\"longitud\":-79.52,\"radio\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value(name));

        org.junit.jupiter.api.Assertions.assertTrue(
                ubicacionRepository.findAll().stream().anyMatch(location -> name.equals(location.getNombre())));
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "administrador")
    void invalidLocationAndScheduleRequestsReturnBadRequest() throws Exception {
        mvc.perform(post("/api/ubicaciones").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/horarios")
                        .contentType("application/json")
                        .content("{\"usuarioId\":999999,\"dia\":8,\"entrada\":\"99:99\"}"))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "administrador")
    void securityPoliciesLoadAndAValidUpdateIsPersistedAndRefreshed() throws Exception {
        SecurityPolicy minimumLength = policyNamed("Longitud mínima de contraseña");
        String originalValue = minimumLength.getValor();
        String updatedValue = String.valueOf(Integer.parseInt(originalValue) + 1);

        try {
            mvc.perform(get("/api/security/policies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(42));

            mvc.perform(put("/api/security/policies/{id}", minimumLength.getId())
                            .contentType("application/json")
                            .content("{\"valor\":\"" + updatedValue + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valor").value(updatedValue));

            org.junit.jupiter.api.Assertions.assertEquals(updatedValue,
                    securityPolicyRepository.findById(minimumLength.getId()).orElseThrow().getValor());
            org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(updatedValue),
                    securityPolicyService.getPasswordMinLength());
        } finally {
            restorePolicy(minimumLength.getId(), originalValue);
        }
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "administrador")
    void invalidSecurityPolicyValuesAreRejectedWithoutChangingTheStoredPolicy() throws Exception {
        assertRejectedValue("Requiere mayúsculas", "not-a-boolean");
        assertRejectedValue("Longitud mínima de contraseña", "-1");
        assertRejectedValue("Horario inicio acceso", "25:99");
        assertRejectedValue("Política de bloqueo", "INVALIDA");
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "administrador")
    void corsPolicyAndIpRulesAreReadOnly() throws Exception {
        SecurityPolicy origins = policyNamed("Orígenes permitidos");
        String originalValue = origins.getValor();
        String testOrigin = "https://security-test.example";

        try {
            mvc.perform(put("/api/security/policies/{id}", origins.getId())
                            .contentType("application/json")
                            .content("{\"valor\":\"" + testOrigin + "\"}"))
                    .andExpect(status().isForbidden());

            org.junit.jupiter.api.Assertions.assertFalse(corsConfigurationSource
                    .getCorsConfiguration(new MockHttpServletRequest())
                    .getAllowedOrigins().contains(testOrigin));

            mvc.perform(put("/api/security/policies/{id}", origins.getId())
                            .contentType("application/json")
                            .content("{\"valor\":\"not-an-origin\"}"))
                    .andExpect(status().isForbidden());

            org.junit.jupiter.api.Assertions.assertEquals(originalValue,
                    securityPolicyRepository.findById(origins.getId()).orElseThrow().getValor());

            mvc.perform(post("/api/security/ip-restrictions")
                            .contentType("application/json")
                            .content("{\"tipo\":\"WHITELIST\",\"direccionIp\":\"999.1.1.1\"}"))
                    .andExpect(status().isForbidden());
        } finally {
            restorePolicy(origins.getId(), originalValue);
        }
    }

    private void assertRejectedValue(String policyName, String invalidValue) throws Exception {
        SecurityPolicy policy = policyNamed(policyName);
        String originalValue = policy.getValor();

        mvc.perform(put("/api/security/policies/{id}", policy.getId())
                        .contentType("application/json")
                        .content("{\"valor\":\"" + invalidValue + "\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(originalValue,
                securityPolicyRepository.findById(policy.getId()).orElseThrow().getValor());
    }

    private SecurityPolicy policyNamed(String name) {
        return securityPolicyRepository.findByNombre(name).stream().findFirst()
                .orElseThrow(() -> new AssertionError("Missing security policy: " + name));
    }

    private void restorePolicy(Long id, String value) {
        SecurityPolicy policy = securityPolicyRepository.findById(id).orElseThrow();
        policy.setValor(value);
        securityPolicyRepository.saveAndFlush(policy);
        securityPolicyService.refreshCache();
    }
}
