package com.sigaa.security;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.config.SecurityConfig;
import com.sigaa.controller.AuthController;
import com.sigaa.model.Usuario;
import com.sigaa.repository.*;
import com.sigaa.service.*;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtro, cadena de seguridad, controlador y transacciones Spring reales; persistencia ficticia sin BD. */
@SpringJUnitWebConfig(RequiredPasswordSecurityTest.Config.class)
class RequiredPasswordSecurityTest {
    private static final String EMAIL = "usuario@example.test";
    private static final String CHANGE = "/api/auth/me/required-password";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({SecurityConfig.class, JwtFilter.class, AuthController.class, GlobalExceptionHandler.class,
            PasswordHistoryService.class, SecurityPasswordService.class, FunctionalApi.class})
    static class Config {
        @Bean JwtUtil jwtUtil() { return mock(JwtUtil.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean SecurityIpService ip() { return mock(SecurityIpService.class); }
        @Bean SecurityPolicyService policy() { return mock(SecurityPolicyService.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean UsuarioUnidadRepository unidades() { return mock(UsuarioUnidadRepository.class); }
        @Bean VwUsuarioRolRepository roles() { return mock(VwUsuarioRolRepository.class); }
        @Bean Usuario2faRepository twoFactor() { return mock(Usuario2faRepository.class); }
        @Bean TwoFactorService twoFactorService() { return mock(TwoFactorService.class); }
        @Bean LoginRateLimitService limiter() { return mock(LoginRateLimitService.class); }
        @Bean PasswordResetService resetService() { return mock(PasswordResetService.class); }
        @Bean PasswordHistoryRepository history() { return mock(PasswordHistoryRepository.class); }
        @Bean AuditLogRepository audits() { return mock(AuditLogRepository.class); }
        @Bean TestTransactions transactionManager() { return new TestTransactions(); }
    }

    @RestController
    static class FunctionalApi {
        @GetMapping({"/api/documentos", "/api/actas", "/api/notas", "/api/reportes", "/api/usuarios", "/api/config/seguridad"})
        @PreAuthorize("hasAuthority('Documentos:Ver')")
        public Map<String, String> read() { return Map.of("result", "allowed"); }
        @GetMapping({"/api/config", "/api/asistencia/sesiones/CODIGO-PRUEBA"})
        public Map<String, String> publicInfo() { return Map.of("result", "public"); }
        @PostMapping("/api/asistencia/invitado")
        public Map<String, String> guest() { return Map.of("result", "public"); }
    }

    // Simula registros confirmados y escrituras pendientes. Spring decide commit/rollback.
    static class TestTransactions extends AbstractPlatformTransactionManager {
        Usuario committed;
        Usuario pending;
        Usuario managed;
        boolean failCommit;
        boolean wroteWithinTransaction;
        TestTransactions() { setRollbackOnCommitFailure(true); }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { pending = null; managed = null; }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("Fallo ficticio al confirmar");
            if (pending != null) committed = copy(pending);
            else if (managed != null) committed = copy(managed);
            pending = null; managed = null;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { pending = null; managed = null; }
        Usuario load() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) return copy(committed);
            if (managed == null) managed = copy(committed);
            return managed;
        }
        Usuario save(Usuario user) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                wroteWithinTransaction = true;
                pending = copy(user);
            } else committed = copy(user);
            return user;
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtUtil jwt;
    @Autowired UserDetailsService details;
    @Autowired SecurityIpService ip;
    @Autowired SecurityPolicyService policy;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioUnidadRepository unidades;
    @Autowired VwUsuarioRolRepository roles;
    @Autowired Usuario2faRepository twoFactor;
    @Autowired TwoFactorService twoFactorService;
    @Autowired LoginRateLimitService limiter;
    @Autowired PasswordResetService recovery;
    @Autowired PasswordHistoryRepository history;
    @Autowired AuditLogRepository audits;
    @Autowired TestTransactions transactions;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(jwt, details, ip, policy, usuarios, unidades, roles, twoFactor, twoFactorService, limiter, recovery, history, audits);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Usuario user = new Usuario();
        user.setId(10L); user.setNombre("Usuario de prueba"); user.setCorreo(EMAIL); user.setUsername("usuario_prueba");
        // Fixture local; nunca procede de datos institucionales.
        user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("TEST_ONLY_NOT_REAL"));
        user.setTokenVersion(4L); user.setCambioContrasenaRequerido(true);
        user.setContrasenaTemporalExpiraEn(LocalDateTime.now().plusMinutes(30));
        transactions.committed = user; transactions.pending = null; transactions.failCommit = false;
        transactions.wroteWithinTransaction = false;
        when(usuarios.findByCorreoIgnoreCase(EMAIL)).thenAnswer(i -> Optional.of(transactions.load()));
        when(usuarios.findByCorreo(EMAIL)).thenAnswer(i -> Optional.of(transactions.load()));
        when(usuarios.findByUsernameIgnoreCase("usuario_prueba")).thenAnswer(i -> Optional.of(transactions.load()));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> transactions.save(i.getArgument(0)));
        when(usuarios.revokeSessionsByCorreo(EMAIL)).thenAnswer(i -> {
            Usuario current = transactions.load();
            current.revokeSessions(); transactions.save(current); return 1;
        });
        when(ip.checkAccess(anyString())).thenReturn(new SecurityIpService.AccessCheckResult(true, null));
        when(jwt.validateToken(anyString())).thenReturn(true);
        when(jwt.extractUsername(anyString())).thenReturn(EMAIL);
        when(jwt.extractLongClaim("session-before", "token_version")).thenReturn(4L);
        when(jwt.extractLongClaim("session-after", "token_version")).thenReturn(5L);
        when(details.loadUserByUsername(EMAIL)).thenReturn(User.withUsername(EMAIL).password("TEST_ONLY_HASH")
                .authorities("Documentos:Ver").build());
        when(policy.getPasswordMinLength()).thenReturn(12);
        when(policy.getPasswordMaxLength()).thenReturn(128);
        when(policy.getPasswordHistorySize()).thenReturn(5);
        when(limiter.checkLogin(anyString(), anyString())).thenReturn(new LoginRateLimitService.Decision(true, 0));
        when(limiter.checkSecondFactor(anyString(), anyString())).thenReturn(new LoginRateLimitService.Decision(true, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/documentos", "/api/actas", "/api/notas", "/api/reportes", "/api/usuarios", "/api/config/seguridad", "/api/auth/me/profile", "/api/config", "/api/asistencia/sesiones/CODIGO-PRUEBA"})
    void directApisRejectTemporarySessionsWithBearerAndCookie(String path) throws Exception {
        for (boolean cookie : List.of(false, true)) {
            mvc.perform(session(get(path), cookie)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
        verifyNoInteractions(details, unidades, roles);
    }

    @Test void normalSessionStillNeedsFunctionalPermissions() throws Exception {
        transactions.committed.setCambioContrasenaRequerido(false);
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isOk());
        when(details.loadUserByUsername(EMAIL)).thenReturn(User.withUsername(EMAIL).password("TEST_ONLY_HASH").authorities("Otro:Ver").build());
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isForbidden());
    }

    @Test void logoutRevokesCapturedBearerAndCookieSessionsAfterCommit() throws Exception {
        transactions.committed.setCambioContrasenaRequerido(false);
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isOk());
        mvc.perform(session(post("/api/auth/logout"), false)).andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(transactions.committed.getTokenVersion()).isEqualTo(5L);
        assertThat(transactions.wroteWithinTransaction).isTrue();
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isUnauthorized());
        mvc.perform(session(get("/api/documentos"), true)).andExpect(status().isUnauthorized());
        verify(usuarios).revokeSessionsByCorreo(EMAIL);
    }

    @Test void expiredRegularPasswordsCannotKeepSessionsOrMintNewOnes() throws Exception {
        transactions.committed.setCambioContrasenaRequerido(false);
        transactions.committed.setFechaExpiracion(LocalDateTime.now().minusMinutes(1));
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("PASSWORD_EXPIRED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"usuario_prueba\",\"password\":\"TEST_ONLY_NOT_REAL\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PASSWORD_EXPIRED"));
        mvc.perform(session(post("/api/auth/logout"), true)).andExpect(status().isNoContent());
    }

    @Test void meReturnsOnlyIdentityAndRequiredChangeWithoutLoadingPermissions() throws Exception {
        mvc.perform(session(get("/api/auth/me"), true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Usuario de prueba"))
                .andExpect(jsonPath("$.correo").value(EMAIL))
                .andExpect(jsonPath("$.cambioContrasenaRequerido").value(true))
                .andExpect(jsonPath("$.roles").doesNotExist()).andExpect(jsonPath("$.permisos").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.fotografiaRuta").doesNotExist());
        verifyNoInteractions(roles, unidades, twoFactor);
        mvc.perform(session(get("/api/auth/me/password-policy"), false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.minLength").value(12));
    }

    @Test void allowlistDoesNotPermitOtherMethodsSubresourcesOrOrdinaryPasswordChange() throws Exception {
        for (var request : List.of(post("/api/auth/me"), get(CHANGE), patch("/api/auth/me/password"),
                get("/api/auth/me/required-password/extra"), post("/api/asistencia/invitado"), put("/api/auth/me/profile"))) {
            mvc.perform(session(request, false)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
    }

    @Test void successfulChangeCommitsStateAndRevokesOldSessionsBeforeNormalLogin() throws Exception {
        mvc.perform(session(change("TEST_ONLY_NEW_PASSWORD"), true)).andExpect(status().isOk());
        assertThat(transactions.wroteWithinTransaction).isTrue();
        assertThat(transactions.committed.getCambioContrasenaRequerido()).isFalse();
        assertThat(transactions.committed.getContrasenaTemporalExpiraEn()).isNull();
        assertThat(transactions.committed.getTokenVersion()).isEqualTo(5L);
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documentos").servletPath("/api/documentos").header("Authorization", "Bearer session-after"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").servletPath("/api/auth/me").header("Authorization", "Bearer session-after"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cambioContrasenaRequerido").value(false));
    }

    @Test void rejectedPasswordLeavesRestrictionAndSessionVersionIntact() throws Exception {
        mvc.perform(session(change("short"), false)).andExpect(status().isBadRequest());
        remainsRestricted();
        verify(history, never()).save(any());
    }

    @Test void reusedPasswordLeavesRestrictionIntact() throws Exception {
        mvc.perform(session(change("TEST_ONLY_NOT_REAL"), false)).andExpect(status().isBadRequest());
        remainsRestricted();
    }

    @Test void failedCommitRollsBackRestrictionExpiryAndSessionVersion() throws Exception {
        transactions.failCommit = true;
        mvc.perform(session(change("TEST_ONLY_NEW_PASSWORD"), false)).andExpect(status().isConflict());
        assertThat(transactions.wroteWithinTransaction).isTrue();
        remainsRestricted();
    }

    @Test void caughtFailureAfterEntityMutationCannotCommitAnUnrestrictedUser() throws Exception {
        when(history.save(any())).thenThrow(new IllegalArgumentException("Historial ficticio rechazado"));
        mvc.perform(session(change("TEST_ONLY_NEW_PASSWORD"), false)).andExpect(status().isBadRequest());
        verify(history).save(any());
        remainsRestricted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/documentos", "/api/auth/me", "/api/auth/me/password-policy", CHANGE})
    void alreadyIssuedSessionCannotOutliveTemporaryPasswordExpiry(String path) throws Exception {
        transactions.committed.setContrasenaTemporalExpiraEn(LocalDateTime.now().minusSeconds(1));
        mvc.perform(session(path.equals(CHANGE) ? change("TEST_ONLY_NEW_PASSWORD") : get(path), true))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
        verify(history, never()).save(any());
    }

    @Test void logoutAndExistingRecoveryRemainAvailableWithAnExpiredCookie() throws Exception {
        transactions.committed.setContrasenaTemporalExpiraEn(LocalDateTime.now().minusSeconds(1));
        mvc.perform(session(post("/api/auth/logout"), true)).andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        when(recovery.requestReset(EMAIL, "127.0.0.1")).thenReturn(PasswordResetService.GENERIC_MESSAGE);
        mvc.perform(session(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + EMAIL + "\"}"), true)).andExpect(status().isOk());
    }

    @Test void expiredPasswordCannotMintSessionsViaLoginOrSecondFactor() throws Exception {
        transactions.committed.setContrasenaTemporalExpiraEn(LocalDateTime.now().minusSeconds(1));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"usuario_prueba\",\"password\":\"TEST_ONLY_NOT_REAL\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
        when(jwt.hasClaim("pending-test", "2fa_pending")).thenReturn(true);
        when(twoFactorService.verifyCode(EMAIL, "123456")).thenReturn(true);
        mvc.perform(post("/api/auth/2fa/authenticate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tempToken\":\"pending-test\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
        verify(jwt, never()).generateToken(anyString(), anyString(), anyLong());
    }

    @Test void firstLoginWithoutTemporaryExpiryStillRequiresChange() throws Exception {
        transactions.committed.setContrasenaTemporalExpiraEn(null);
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isForbidden());
        mvc.perform(session(change("TEST_ONLY_NEW_PASSWORD"), false)).andExpect(status().isOk());
    }

    @Test void temporaryLoginDoesNotExposeFunctionalRolesOrPermissions() throws Exception {
        when(jwt.generateToken(EMAIL, "", 4L)).thenReturn("session-before");
        when(policy.getJwtExpirationMs()).thenReturn(3_600_000L);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"usuario_prueba\",\"password\":\"TEST_ONLY_NOT_REAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cambioContrasenaRequerido").value(true))
                .andExpect(jsonPath("$.roles").value(""))
                .andExpect(jsonPath("$.permisos").isEmpty());
        verifyNoInteractions(roles, unidades);
    }

    @Test void anonymousSecurityAndPublicEntryPointsArePreserved() throws Exception {
        for (String path : List.of("/api/documentos", "/api/actas", "/api/auth/me")) {
            mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(change("TEST_ONLY_NEW_PASSWORD").servletPath(CHANGE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/config").servletPath("/api/config")).andExpect(status().isOk());
        mvc.perform(get("/api/asistencia/sesiones/CODIGO-PRUEBA").servletPath("/api/asistencia/sesiones/CODIGO-PRUEBA"))
                .andExpect(status().isOk());
    }

    private void remainsRestricted() throws Exception {
        assertThat(transactions.committed.getCambioContrasenaRequerido()).isTrue();
        assertThat(transactions.committed.getContrasenaTemporalExpiraEn()).isNotNull();
        assertThat(transactions.committed.getTokenVersion()).isEqualTo(4L);
        mvc.perform(session(get("/api/documentos"), false)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    private MockHttpServletRequestBuilder change(String password) {
        return patch(CHANGE).contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + password + "\"}");
    }
    private MockHttpServletRequestBuilder session(MockHttpServletRequestBuilder request, boolean cookie) {
        request.with(req -> { req.setServletPath(req.getRequestURI()); return req; });
        return cookie ? request.cookie(new Cookie("sigaa_access", "session-before"))
                : request.header("Authorization", "Bearer session-before");
    }
    private static Usuario copy(Usuario source) {
        Usuario user = new Usuario();
        user.setId(source.getId()); user.setNombre(source.getNombre()); user.setCorreo(source.getCorreo());
        user.setUsername(source.getUsername()); user.setPassword(source.getPassword());
        user.setActivo(source.getActivo()); user.setBloqueado(source.getBloqueado());
        user.setCambioContrasenaRequerido(source.getCambioContrasenaRequerido());
        user.setContrasenaTemporalExpiraEn(source.getContrasenaTemporalExpiraEn());
        user.setFechaExpiracion(source.getFechaExpiracion()); user.setTokenVersion(source.getTokenVersion());
        return user;
    }
}
