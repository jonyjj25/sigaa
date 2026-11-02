package com.sigaa.service;

import com.sigaa.config.RuntimeDatabasePrivilegeGuard;
import com.sigaa.model.Backup;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.BackupRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "sigaa.test.postgres.bin", matches = ".+")
class RuntimeDatabasePrivilegeIntegrationTest {
    static LeastPrivilegePostgresFixture fixture;
    @BeforeAll static void start() throws Exception { fixture = new LeastPrivilegePostgresFixture(); }
    @AfterAll static void stop() throws Exception { if (fixture != null) fixture.close(); }

    @Test void runtimeHasCrudSequencesAndFunctionsWithoutAdministrativePrivileges() throws Exception {
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "CREATE TABLE a08_crud (id BIGSERIAL PRIMARY KEY, value INTEGER)");
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "CREATE FUNCTION a08_value() RETURNS INTEGER LANGUAGE SQL AS 'SELECT 1'");
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "CREATE FUNCTION a08_owner_value() RETURNS INTEGER LANGUAGE SQL SECURITY DEFINER AS 'SELECT 1'");
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "GRANT SELECT, INSERT, UPDATE, DELETE ON a08_crud TO sigaa_runtime");
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "GRANT USAGE, SELECT ON SEQUENCE a08_crud_id_seq TO sigaa_runtime");
        LeastPrivilegePostgresFixture.execute(fixture.owner(), "GRANT EXECUTE ON FUNCTION a08_value() TO sigaa_runtime");
        var jdbc = new JdbcTemplate(fixture.runtime());
        Long id = jdbc.queryForObject("INSERT INTO a08_crud(value) VALUES (1) RETURNING id", Long.class);
        assertEquals(1, jdbc.queryForObject("SELECT a08_value()", Integer.class));
        assertEquals(1, jdbc.update("UPDATE a08_crud SET value = 2 WHERE id = ?", id));
        assertEquals(2, jdbc.queryForObject("SELECT value FROM a08_crud WHERE id = ?", Integer.class, id));
        assertEquals(1, jdbc.update("DELETE FROM a08_crud WHERE id = ?", id));
        new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated();
        for (String sql : new String[]{"CREATE DATABASE a08_forbidden", "CREATE ROLE a08_forbidden", "CREATE TABLE a08_forbidden(id INT)",
                "CREATE TEMP TABLE a08_forbidden(id INT)", "TRUNCATE usuarios", "ALTER TABLE usuarios ADD COLUMN a08_forbidden INT",
                "SELECT a08_owner_value()"}) {
            SQLException denied = assertThrows(SQLException.class,
                    () -> LeastPrivilegePostgresFixture.execute(fixture.runtime(), sql), sql);
            assertEquals("42501", denied.getSQLState());
        }
        assertNull(new JdbcTemplate(fixture.runtime()).queryForObject("SELECT to_regclass('public.flyway_schema_history')", String.class));
        assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.owner()).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.postgres.dataSource).afterSingletonsInstantiated());
    }

    @Test void scramRejectsWrongCredentialsAndReprovisionDoesNotRotateExistingPasswords() throws Exception {
        SQLException wrong = assertThrows(SQLException.class, () -> fixture.source("sigaa_runtime", "TEST_ONLY_WRONG").getConnection());
        assertEquals("28P01", wrong.getSQLState());
        fixture.provision("TEST_ONLY_OTHER_runtime", "TEST_ONLY_OTHER_backup");
        new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated();
        try (var connection = fixture.owner().getConnection()) { assertTrue(connection.isValid(1)); }
        try (var connection = fixture.backup().getConnection()) { assertTrue(connection.isValid(1)); }
    }

    @Test void guardRejectsAnElevatedRuntimeOrRoleMembership() throws Exception {
        var admin = fixture.postgres.dataSource;
        try {
            LeastPrivilegePostgresFixture.execute(admin, "ALTER ROLE sigaa_runtime CREATEDB");
            assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated());
        } finally { LeastPrivilegePostgresFixture.execute(admin, "ALTER ROLE sigaa_runtime NOCREATEDB"); }
        LeastPrivilegePostgresFixture.execute(admin, "CREATE ROLE a08_extra NOLOGIN");
        try {
            LeastPrivilegePostgresFixture.execute(admin, "GRANT a08_extra TO sigaa_runtime");
            assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated());
        } finally { LeastPrivilegePostgresFixture.execute(admin, "REVOKE a08_extra FROM sigaa_runtime"); }
        try {
            LeastPrivilegePostgresFixture.execute(admin, "ALTER DATABASE sigaa_security OWNER TO sigaa_runtime");
            LeastPrivilegePostgresFixture.execute(admin, "REVOKE CREATE, TEMP ON DATABASE sigaa_security FROM sigaa_runtime");
            assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated());
        } finally {
            LeastPrivilegePostgresFixture.execute(admin, "ALTER DATABASE sigaa_security OWNER TO sigaa_a05_test");
            LeastPrivilegePostgresFixture.execute(admin, "GRANT CONNECT ON DATABASE sigaa_security TO sigaa_runtime, sigaa_backup");
        }
        try {
            LeastPrivilegePostgresFixture.execute(admin, "CREATE DATABASE a08_other_owned OWNER sigaa_runtime");
            assertThrows(IllegalStateException.class, () -> new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated());
        } finally { LeastPrivilegePostgresFixture.execute(admin, "DROP DATABASE IF EXISTS a08_other_owned"); }
        new RuntimeDatabasePrivilegeGuard(fixture.runtime()).afterSingletonsInstantiated();
    }

    @Test void backupIsReadOnlyOnSourceAndCanDumpEncryptRestoreAndDropOnlyItsTemporaryDatabase() throws Exception {
        assertEquals("42501", assertThrows(SQLException.class, () -> LeastPrivilegePostgresFixture.execute(fixture.backup(),
                "UPDATE usuarios SET nombre = 'forbidden' WHERE id = 1")).getSQLState());
        assertEquals("42501", assertThrows(SQLException.class, () -> LeastPrivilegePostgresFixture.execute(fixture.backup(),
                "DROP DATABASE sigaa_security")).getSQLState());
        var repository = mock(BackupRepository.class);
        when(repository.save(any(Backup.class))).thenAnswer(invocation -> {
            Backup backup = invocation.getArgument(0); backup.setId(1L);
            if (backup.getFecha() == null) backup.setFecha(LocalDateTime.now()); return backup;
        });
        Path artifacts = fixture.postgres.root.resolve("least-privilege-backup");
        var restrictedTemporaryDatabase = new java.util.concurrent.atomic.AtomicBoolean();
        var service = new BackupService(repository, mock(AuditLogRepository.class), new SimpleMeterRegistry(),
                fixture.url, "sigaa_backup", LeastPrivilegePostgresFixture.BACKUP_PASSWORD,
                artifacts.resolve("local").toString(), artifacts.resolve("external").toString(),
                fixture.postgres.tool("pg_dump").toString(), fixture.postgres.tool("pg_restore").toString(),
                Base64.getEncoder().encodeToString(new byte[32]), 30, 30, false, false,
                "0 0 2 * * *", "0 0 4 * * SUN", "America/Panama", Duration.ofSeconds(60), Duration.ofSeconds(2)) {
            @Override protected Process startProcess(java.util.List<String> command, String password) throws java.io.IOException {
                if (command.getFirst().equals(fixture.postgres.tool("pg_restore").toString())) {
                    String database = command.get(command.indexOf("-d") + 1);
                    var blocked = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                            fixture.url.replace("/sigaa_security", "/" + database), "sigaa_runtime", LeastPrivilegePostgresFixture.RUNTIME_PASSWORD);
                    assertEquals("42501", assertThrows(SQLException.class, blocked::getConnection).getSQLState());
                    restrictedTemporaryDatabase.set(true);
                }
                return super.startProcess(command, password);
            }
        };
        Backup backup = service.create("manual", "a08@example.test");
        when(repository.findById(1L)).thenReturn(Optional.of(backup));
        assertEquals("completado", backup.getEstado());
        assertEquals(-1L, Files.mismatch(Path.of(backup.getRutaArchivo()), artifacts.resolve("external").resolve(backup.getNombre())));
        assertEquals(true, service.testRestore(1L, "a08@example.test").get("ok"));
        assertTrue(restrictedTemporaryDatabase.get());
        assertEquals(0, new JdbcTemplate(fixture.postgres.dataSource).queryForObject(
                "SELECT COUNT(*) FROM pg_database WHERE datname LIKE 'sigaa_restore_%'", Integer.class));
    }
}
