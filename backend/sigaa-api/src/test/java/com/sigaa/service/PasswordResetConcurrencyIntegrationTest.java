package com.sigaa.service;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.controller.AuthController;
import com.sigaa.model.PasswordResetRequest;
import com.sigaa.model.PasswordHistory;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.PasswordHistoryRepository;
import com.sigaa.repository.PasswordResetRequestRepository;
import com.sigaa.repository.UsuarioRepository;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PostgreSQL nuevo y exclusivo de estas pruebas, con conexiones/ transacciones reales.
 * Ejecución: -Dsigaa.test.postgres.bin=<directorio de initdb y postgres>.
 * No carga application.properties, perfiles ni configuración de bases existentes.
 */
@EnabledIfSystemProperty(named = "sigaa.test.postgres.bin", matches = ".+")
@SpringJUnitConfig(PasswordResetConcurrencyIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetConcurrencyIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = PasswordResetRequestRepository.class,
            includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                    PasswordResetRequestRepository.class, UsuarioRepository.class,
                    PasswordHistoryRepository.class, AuditLogRepository.class}))
    @Import({PasswordResetService.class, SecurityPasswordService.class})
    static class Config {
        @Bean(destroyMethod = "close") EphemeralPostgres postgres() throws Exception { return new EphemeralPostgres(); }
        @Bean DataSource dataSource(EphemeralPostgres postgres) { return postgres.dataSource; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    "com.sigaa.model.PasswordResetRequest", "com.sigaa.model.Usuario", "com.sigaa.model.PasswordHistory",
                    "com.sigaa.model.AuditLog", "com.sigaa.model.Role", "com.sigaa.model.Permission",
                    "com.sigaa.model.UsuarioUnidad", "com.sigaa.model.Unidad"));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create", "hibernate.show_sql", "false"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean SecurityPolicyService policies() { return mock(SecurityPolicyService.class); }
        @Bean TwoFactorService mail() { return mock(TwoFactorService.class); }
        @Bean ControlledEncoder encoder() { return new ControlledEncoder(); }
        @Bean ControlledHistory history(PasswordHistoryRepository repository, SecurityPolicyService policies, PasswordEncoder encoder) {
            return new ControlledHistory(repository, policies, encoder);
        }
    }

    private static final String EMAIL = "recuperacion@example.test";
    private static final String OTHER_EMAIL = "otro@example.test";
    private static final String CODE = "123456"; // Ficticio y exclusivo de la base temporal.
    private static final String TOKEN = "TEST_ONLY_NOT_REAL_RESET_TOKEN";
    private static final String OLD_PASSWORD = "Test_Only_Old_123!";
    private static final String NEW_PASSWORD = "Test_Only_New_456!";
    @Autowired PasswordResetService service;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired UsuarioRepository users;
    @Autowired PasswordHistoryRepository historyRepository;
    @Autowired AuditLogRepository audits;
    @Autowired ControlledHistory history;
    @Autowired ControlledEncoder encoder;
    @Autowired SecurityPolicyService policies;
    @Autowired TwoFactorService mail;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private Usuario user;
    private MockMvc mvc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(transactionManager);
        jdbc.execute("DROP TRIGGER IF EXISTS a05_fail_commit ON usuarios");
        requests.deleteAll(); historyRepository.deleteAll(); audits.deleteAll(); users.deleteAll();
        reset(policies, mail);
        history.gate.set(null); history.failure.set(null); encoder.gate.set(null);
        when(policies.getPasswordMinLength()).thenReturn(8);
        when(policies.getPasswordMaxLength()).thenReturn(128);
        when(policies.requiresUppercase()).thenReturn(true);
        when(policies.requiresLowercase()).thenReturn(true);
        when(policies.requiresDigits()).thenReturn(true);
        when(policies.requiresSpecialChars()).thenReturn(true);
        when(policies.getPasswordHistorySize()).thenReturn(5);
        when(policies.getPasswordExpirationDays()).thenReturn(90);
        when(mail.isMailAvailable()).thenReturn(true);
        user = createUser(EMAIL);
        // Evita ejecutar el constructor de AuthController, que crea una carpeta de fotografías.
        AuthController controller = mock(AuthController.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller, "passwordResetService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void validCodeAndTokenWorkOnceAndPreserveExistingPasswordAndSessionPolicies() {
        tx.executeWithoutResult(status -> {
            var lockedUser = users.findById(user.getId()).orElseThrow();
            lockedUser.setBloqueado(true); lockedUser.setIntentosFallidos(3);
            lockedUser.setBloqueadoHasta(LocalDateTime.now().plusMinutes(30)); users.save(lockedUser);
        });
        var request = createRequest(user, false);
        var verification = service.verifyCode(EMAIL, CODE, "127.0.0.1");
        assertEquals(PasswordResetService.Status.SUCCESS, verification.status());
        assertNotNull(verification.resetToken());
        assertEquals(PasswordResetService.Status.INVALID, service.verifyCode(EMAIL, CODE, "127.0.0.1").status());
        assertFalse(requests.findById(request.getId()).orElseThrow().isBlocked());
        var result = service.resetPassword(verification.resetToken(), NEW_PASSWORD, "127.0.0.1");
        assertEquals(PasswordResetService.Status.SUCCESS, result.status());
        Usuario updated = users.findById(user.getId()).orElseThrow();
        assertTrue(encoder.matches(NEW_PASSWORD, updated.getPassword()));
        assertEquals(1L, updated.getTokenVersion());
        assertFalse(updated.getCambioContrasenaRequerido());
        assertNull(updated.getContrasenaTemporalExpiraEn());
        assertNotNull(updated.getFechaExpiracion());
        assertFalse(updated.getBloqueado());
        assertEquals(0, updated.getIntentosFallidos());
        assertNull(updated.getBloqueadoHasta());
        PasswordResetRequest consumed = requests.findById(request.getId()).orElseThrow();
        assertTrue(consumed.isUsed()); assertNotNull(consumed.getUsedAt()); assertNull(consumed.getResetTokenHash());
        assertEquals(1, historyRepository.findTop100ByUsuarioIdOrderByCreatedAtDesc(user.getId()).size());
        assertEquals(PasswordResetService.Status.INVALID, service.resetPassword(verification.resetToken(), NEW_PASSWORD, "127.0.0.1").status());
        verify(mail, times(1)).sendPasswordChangedEmail(EMAIL);
    }

    @Test void expiredCodeCannotIssueAToken() {
        var request = createRequest(user, false);
        edit(request.getId(), item -> item.setExpiresAt(LocalDateTime.now().minusSeconds(1)));
        var result = service.verifyCode(EMAIL, CODE, "127.0.0.1");
        assertEquals(PasswordResetService.Status.EXPIRED, result.status()); assertNull(result.resetToken());
        assertNoPasswordChange();
    }

    @ParameterizedTest @ValueSource(strings = {"expired", "used", "blocked", "unverified"})
    void unavailableTokensAreRejected(String state) {
        var request = createRequest(user, true);
        edit(request.getId(), item -> {
            switch (state) {
                case "expired" -> item.setResetTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
                case "used" -> item.setUsed(true);
                case "blocked" -> item.setBlocked(true);
                case "unverified" -> item.setCodeVerifiedAt(null);
                default -> throw new IllegalArgumentException(state);
            }
        });
        assertEquals(PasswordResetService.Status.INVALID, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
        assertNoPasswordChange();
    }

    @Test void nonexistentAndIncorrectCredentialsDoNotChangeAPassword() {
        assertEquals(PasswordResetService.Status.INVALID, service.verifyCode(EMAIL, CODE, "127.0.0.1").status());
        assertEquals(PasswordResetService.Status.INVALID, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
        var request = createRequest(user, false);
        assertEquals(PasswordResetService.Status.INVALID, service.verifyCode(EMAIL, "654321", "127.0.0.1").status());
        PasswordResetRequest persisted = requests.findById(request.getId()).orElseThrow();
        assertEquals(1, persisted.getAttempts()); assertNull(persisted.getCodeVerifiedAt());
        assertNull(persisted.getResetTokenHash()); assertFalse(persisted.isUsed());
        assertNoPasswordChange();
    }

    @Test void codeForAnotherUserIsRejectedAndTheOwnerCanStillUseIt() {
        createUser(OTHER_EMAIL);
        createRequest(user, false);
        assertEquals(PasswordResetService.Status.INVALID, service.verifyCode(OTHER_EMAIL, CODE, "127.0.0.1").status());
        var result = service.verifyCode(EMAIL, CODE, "127.0.0.1");
        assertEquals(PasswordResetService.Status.SUCCESS, result.status());
        assertEquals(PasswordResetService.Status.SUCCESS, service.resetPassword(result.resetToken(), NEW_PASSWORD, "127.0.0.1").status());
        assertTrue(encoder.matches(OLD_PASSWORD, users.findByCorreo(OTHER_EMAIL).orElseThrow().getPassword()));
    }

    @Test void passwordPolicyRejectionDoesNotConsumeTheToken() {
        var request = createRequest(user, true);
        assertEquals(PasswordResetService.Status.WEAK_PASSWORD, service.resetPassword(TOKEN, "weak", "127.0.0.1").status());
        assertFalse(requests.findById(request.getId()).orElseThrow().isUsed());
        assertNoPasswordChange();
        assertEquals(PasswordResetService.Status.SUCCESS, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
    }

    @Test void currentAndHistoricalPasswordsRemainForbiddenWithoutConsumingTheToken() {
        createRequest(user, true);
        assertEquals(PasswordResetService.Status.WEAK_PASSWORD, service.resetPassword(TOKEN, OLD_PASSWORD, "127.0.0.1").status());
        PasswordHistory previous = new PasswordHistory(); previous.setUsuarioId(user.getId());
        previous.setPasswordHash(encoder.encode(NEW_PASSWORD)); historyRepository.saveAndFlush(previous);
        var result = service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1");
        assertEquals(PasswordResetService.Status.WEAK_PASSWORD, result.status());
        assertEquals("No puede reutilizar una contraseña reciente", result.message());
        assertTrue(encoder.matches(OLD_PASSWORD, users.findById(user.getId()).orElseThrow().getPassword()));
        assertEquals(0L, users.findById(user.getId()).orElseThrow().getTokenVersion());
        assertEquals(1, historyRepository.count());
        verify(mail, never()).sendPasswordChangedEmail(anyString());
        assertFalse(requests.findAll().getFirst().isUsed());
    }

    @ParameterizedTest @ValueSource(strings = {"runtime", "validation"})
    void aFailureAfterChangingTheManagedUserRollsBackPasswordHistorySessionsAndConsumption(String failureKind) {
        var request = createRequest(user, true);
        history.failure.set(failureKind.equals("runtime") ? new IllegalStateException("Fallo ficticio")
                : new IllegalArgumentException("Fallo ficticio de validación"));
        if (failureKind.equals("runtime")) assertThrows(IllegalStateException.class,
                () -> service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1"));
        else assertEquals(PasswordResetService.Status.WEAK_PASSWORD, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
        assertNoPasswordChange();
        PasswordResetRequest available = requests.findById(request.getId()).orElseThrow();
        assertFalse(available.isUsed()); assertNull(available.getUsedAt()); assertNotNull(available.getResetTokenHash());
        history.failure.set(null);
        assertEquals(PasswordResetService.Status.SUCCESS, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
    }

    @Test void commitFailureRollsBackEverythingAndDoesNotSendAConfirmationEmail() {
        createRequest(user, true);
        // Solo en el cluster nuevo: fallo real y diferido hasta COMMIT.
        jdbc.execute("CREATE OR REPLACE FUNCTION a05_reject_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fallo ficticio al confirmar'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER a05_fail_commit AFTER UPDATE ON usuarios DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION a05_reject_commit()");
        try {
            assertThrows(RuntimeException.class, () -> service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1"));
            assertNoPasswordChange();
            assertFalse(requests.findAll().getFirst().isUsed());
        } finally { jdbc.execute("DROP TRIGGER a05_fail_commit ON usuarios"); }
        assertEquals(PasswordResetService.Status.SUCCESS, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
    }

    @Test void twoConcurrentVerificationsIssueOnlyOneTokenAndTheLosingRequestDoesNotInvalidateIt() throws Exception {
        createRequest(user, false);
        Gate gate = new Gate(); encoder.gate.set(gate);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.verifyCode(EMAIL, CODE, "127.0.0.1"));
            assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.verifyCode(EMAIL, CODE, "127.0.0.1"));
            try { assertDatabaseWaitsForRowLock(); } finally { gate.release.countDown(); }
            var winner = first.get(10, TimeUnit.SECONDS); var loser = second.get(10, TimeUnit.SECONDS);
            assertEquals(PasswordResetService.Status.SUCCESS, winner.status());
            assertEquals(PasswordResetService.Status.INVALID, loser.status()); assertNull(loser.resetToken());
            assertFalse(requests.findAll().getFirst().isBlocked());
            assertEquals(PasswordResetService.Status.SUCCESS, service.resetPassword(winner.resetToken(), NEW_PASSWORD, "127.0.0.1").status());
        } finally { gate.release.countDown(); encoder.gate.set(null); }
    }

    @Test void twoConcurrentResetsWithTheSameTokenHaveExactlyOneSuccess() throws Exception {
        var request = createRequest(user, true);
        Gate gate = new Gate(); history.gate.set(gate);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1"));
            assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.resetPassword(TOKEN, "Test_Only_Other_789!", "127.0.0.1"));
            try { assertDatabaseWaitsForRowLock(); } finally { gate.release.countDown(); }
            assertEquals(PasswordResetService.Status.SUCCESS, first.get(10, TimeUnit.SECONDS).status());
            assertEquals(PasswordResetService.Status.INVALID, second.get(10, TimeUnit.SECONDS).status());
        } finally { gate.release.countDown(); history.gate.set(null); }
        assertTrue(requests.findById(request.getId()).orElseThrow().isUsed());
        assertTrue(encoder.matches(NEW_PASSWORD, users.findById(user.getId()).orElseThrow().getPassword()));
        assertEquals(1L, users.findById(user.getId()).orElseThrow().getTokenVersion());
        assertEquals(1, historyRepository.count());
        verify(mail, times(1)).sendPasswordChangedEmail(EMAIL);
    }

    @Test void tokenExpirationIsRecheckedAfterWaitingForTheLock() throws Exception {
        var request = createRequest(user, true);
        Gate gate = new Gate();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> tx.executeWithoutResult(status -> {
                var locked = requests.findByIdForUpdate(request.getId()).orElseThrow();
                gate.pause();
                locked.setResetTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
                requests.save(locked);
            }));
            assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
            var reset = executor.submit(() -> service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1"));
            try { assertDatabaseWaitsForRowLock(); } finally { gate.release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            assertEquals(PasswordResetService.Status.INVALID, reset.get(10, TimeUnit.SECONDS).status());
        } finally { gate.release.countDown(); }
        assertNoPasswordChange();
    }

    @Test void resendInvalidatesThePreviousRecoveryAndKeepsTheGenericResponse() {
        var previous = createRequest(user, true);
        jdbc.update("UPDATE password_reset_requests SET created_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(2), previous.getId());
        assertEquals(PasswordResetService.GENERIC_MESSAGE, service.requestReset(EMAIL, "127.0.0.1"));
        assertTrue(requests.findById(previous.getId()).orElseThrow().isUsed());
        assertEquals(PasswordResetService.Status.INVALID, service.resetPassword(TOKEN, NEW_PASSWORD, "127.0.0.1").status());
        assertNoPasswordChange();
    }

    @Test void httpErrorsAndSuccessExposeOnlyTheExistingPublicContract() throws Exception {
        String invalidBody = mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"resetToken\":\"TEST_ONLY_NOT_REAL_RESET_TOKEN\",\"newPassword\":\"Test_Only_New_456!\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("La sesión de recuperación no es válida o expiró"))
                .andReturn().getResponse().getContentAsString();
        assertSafePublicJson(invalidBody, "error");
        var request = createRequest(user, true);
        doAnswer(invocation -> {
            // Una conexión independiente solo puede ver el consumo después del commit.
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement("SELECT used FROM password_reset_requests WHERE id = ?")) {
                statement.setLong(1, request.getId());
                try (var result = statement.executeQuery()) { assertTrue(result.next()); assertTrue(result.getBoolean(1)); }
            }
            return null;
        }).when(mail).sendPasswordChangedEmail(anyString());
        String successBody = mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"resetToken\":\"TEST_ONLY_NOT_REAL_RESET_TOKEN\",\"newPassword\":\"Test_Only_New_456!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Contraseña actualizada correctamente"))
                .andReturn().getResponse().getContentAsString();
        assertSafePublicJson(successBody, "message");
        mvc.perform(post("/api/auth/verify-reset-code").contentType("application/json")
                        .content("{\"correo\":\"recuperacion@example.test\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.resetToken").doesNotExist());
        String absentAccount = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"correo\":\"ausente@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(PasswordResetService.GENERIC_MESSAGE))
                .andReturn().getResponse().getContentAsString();
        assertSafePublicJson(absentAccount, "message");
    }

    private Usuario createUser(String email) {
        Usuario value = new Usuario(); value.setNombre("Usuario ficticio"); value.setApellido("Prueba");
        value.setCorreo(email); value.setUsername(email); value.setPassword(encoder.encode(OLD_PASSWORD));
        value.setTokenVersion(0L); value.setCambioContrasenaRequerido(true);
        value.setContrasenaTemporalExpiraEn(LocalDateTime.now().plusMinutes(30));
        return users.saveAndFlush(value);
    }

    private PasswordResetRequest createRequest(Usuario owner, boolean verified) {
        var value = new PasswordResetRequest(); value.setUsuario(owner); value.setCodeHash(encoder.encode(CODE));
        value.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        if (verified) { value.setCodeVerifiedAt(LocalDateTime.now()); value.setResetTokenHash(encoder.encode(TOKEN)); value.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(10)); }
        return requests.saveAndFlush(value);
    }

    private void edit(Long id, java.util.function.Consumer<PasswordResetRequest> changes) {
        tx.executeWithoutResult(status -> { var value = requests.findById(id).orElseThrow(); changes.accept(value); requests.save(value); });
    }

    private void assertNoPasswordChange() {
        Usuario unchanged = users.findById(user.getId()).orElseThrow();
        assertTrue(encoder.matches(OLD_PASSWORD, unchanged.getPassword()));
        assertEquals(0L, unchanged.getTokenVersion()); assertEquals(0, historyRepository.count());
        assertTrue(unchanged.getCambioContrasenaRequerido()); assertNotNull(unchanged.getContrasenaTemporalExpiraEn());
        verify(mail, never()).sendPasswordChangedEmail(anyString());
    }

    private void assertDatabaseWaitsForRowLock() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND application_name = 'sigaa-a05-test' AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(25);
        }
        fail("La segunda transacción debe esperar el bloqueo real de PostgreSQL");
    }

    private void assertSafePublicJson(String body, String expectedField) throws Exception {
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertEquals(1, node.size()); assertTrue(node.has(expectedField));
        for (String privateValue : List.of(CODE, TOKEN, OLD_PASSWORD, NEW_PASSWORD, EMAIL)) assertFalse(body.contains(privateValue));
        for (String privateField : List.of("codeHash", "resetToken", "password", "tokenVersion", "usuario")) assertFalse(node.has(privateField));
    }

    static class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        void pause() {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout de sincronización de prueba"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Prueba interrumpida", ex); }
        }
    }

    static class ControlledHistory extends PasswordHistoryService {
        final AtomicReference<Gate> gate = new AtomicReference<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ControlledHistory(PasswordHistoryRepository repository, SecurityPolicyService policies, PasswordEncoder encoder) { super(repository, policies, encoder); }
        @Override public void changePassword(Usuario user, String password) {
            Gate pause = gate.getAndSet(null); if (pause != null) pause.pause();
            super.changePassword(user, password); // Servicio real; solo instrumenta el momento de la carrera/fallo.
            RuntimeException error = failure.get(); if (error != null) throw error;
        }
    }

    static class ControlledEncoder implements PasswordEncoder {
        private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(4);
        final AtomicReference<Gate> gate = new AtomicReference<>();
        @Override public String encode(CharSequence raw) {
            Gate pause = gate.getAndSet(null); if (pause != null) pause.pause();
            return delegate.encode(raw);
        }
        @Override public boolean matches(CharSequence raw, String encoded) { return delegate.matches(raw, encoded); }
    }

    /** Arranca únicamente binarios locales y un cluster recién creado bajo target. */
    static class EphemeralPostgres implements AutoCloseable {
        final Path bin = Path.of(System.getProperty("sigaa.test.postgres.bin")).toAbsolutePath().normalize();
        final Path root;
        final DriverManagerDataSource dataSource;
        Process process;
        Path tool(String name) { return bin.resolve(name + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")); }
        EphemeralPostgres() throws Exception {
            if (!Files.isRegularFile(tool("initdb")) || !Files.isRegularFile(tool("postgres")))
                throw new IllegalStateException("Faltan binarios locales de PostgreSQL para la prueba");
            Path target = Path.of("target").toAbsolutePath().normalize(); Files.createDirectories(target);
            root = Files.createTempDirectory(target, "a05-postgres-");
            if (!root.startsWith(target)) throw new IllegalStateException("El cluster de prueba debe estar dentro de target");
            try {
                Process init = new ProcessBuilder(tool("initdb").toString(), "-D", root.resolve("data").toString(),
                        "-U", "sigaa_a05_test", "-A", "trust", "--encoding=UTF8", "--locale=C", "--no-sync")
                        .redirectErrorStream(true).redirectOutput(root.resolve("init.log").toFile()).start();
                if (!init.waitFor(30, TimeUnit.SECONDS)) {
                    init.descendants().forEach(ProcessHandle::destroyForcibly);
                    init.destroyForcibly();
                    throw new IllegalStateException("Timeout al crear cluster de prueba");
                }
                if (init.exitValue() != 0) throw new IllegalStateException("No se pudo crear el cluster temporal de prueba");
                int port;
                try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
                process = new ProcessBuilder(tool("postgres").toString(), "-D", root.resolve("data").toString(),
                        "-h", "127.0.0.1", "-p", Integer.toString(port), "-F", "-c", "synchronous_commit=off",
                        "-c", "log_statement=none", "-c", "log_min_error_statement=panic")
                        .redirectErrorStream(true).redirectOutput(root.resolve("postgres.log").toFile()).start();
                dataSource = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port
                        + "/postgres?user=sigaa_a05_test&ApplicationName=sigaa-a05-test");
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                boolean ready = false;
                while (System.nanoTime() < deadline && process.isAlive()) {
                    try (var connection = dataSource.getConnection()) { ready = connection.isValid(1); if (ready) break; }
                    catch (java.sql.SQLException ignored) { Thread.sleep(50); }
                }
                if (!ready || !process.isAlive()) throw new IllegalStateException("PostgreSQL temporal no inició");
            } catch (Exception failure) { close(); throw failure; }
        }
        @Override public void close() throws IOException, InterruptedException {
            if (process == null || !process.isAlive()) return;
            Process stop = new ProcessBuilder(tool("pg_ctl").toString(), "-D", root.resolve("data").toString(),
                    "stop", "-m", "fast", "-w", "-t", "10")
                    .redirectErrorStream(true).redirectOutput(root.resolve("stop.log").toFile()).start();
            if (!stop.waitFor(15, TimeUnit.SECONDS)) stop.destroyForcibly();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
        }
    }
}
