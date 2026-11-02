package com.sigaa.service;

import com.sigaa.model.Backup;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.BackupRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reutiliza el cluster desechable de pruebas; no carga ninguna configuración real. */
@EnabledIfSystemProperty(named = "sigaa.test.postgres.bin", matches = ".+")
class BackupPostgresIntegrationTest {
    @Test void nativeDumpEncryptionExternalCopyAndRestoreWorkOnAFreshFictionalDatabase() throws Exception {
        try (var postgres = new PasswordResetConcurrencyIntegrationTest.EphemeralPostgres()) {
            try (var connection = postgres.dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY, activo BOOLEAN NOT NULL)");
                statement.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, nombre VARCHAR(100) NOT NULL)");
                statement.execute("CREATE TABLE usuario_roles (usuario_id BIGINT NOT NULL REFERENCES usuarios(id), rol_id BIGINT NOT NULL REFERENCES roles(id))");
                statement.execute("CREATE TABLE permisos (id BIGINT PRIMARY KEY)");
                statement.execute("INSERT INTO usuarios VALUES (1, TRUE)");
                statement.execute("INSERT INTO roles VALUES (1, 'Superadmin')");
                statement.execute("INSERT INTO usuario_roles VALUES (1, 1)");
                statement.execute("CREATE TABLE a06_fixture (value INTEGER NOT NULL)");
                statement.execute("INSERT INTO a06_fixture VALUES (1)");
            }
            var repository = mock(BackupRepository.class);
            when(repository.save(any(Backup.class))).thenAnswer(invocation -> {
                Backup b = invocation.getArgument(0); b.setId(1L);
                if (b.getFecha() == null) b.setFecha(LocalDateTime.now());
                return b;
            });
            Path artifacts = postgres.root.resolve("artifacts");
            BackupService service = new BackupService(repository, mock(AuditLogRepository.class), new SimpleMeterRegistry(),
                    postgres.dataSource.getUrl().split("\\?")[0], "sigaa_a05_test", "TEST_ONLY_NOT_REAL",
                    artifacts.resolve("local").toString(), artifacts.resolve("external").toString(),
                    postgres.tool("pg_dump").toString(), postgres.tool("pg_restore").toString(),
                    Base64.getEncoder().encodeToString(new byte[32]), 30, 30, false, false,
                    "0 0 2 * * *", "0 0 4 * * SUN", "America/Panama", Duration.ofSeconds(30), Duration.ofSeconds(2));
            Backup backup = service.create("manual", "prueba@example.test");
            when(repository.findById(1L)).thenReturn(Optional.of(backup));
            assertEquals("completado", backup.getEstado());
            assertTrue(Files.mismatch(Path.of(backup.getRutaArchivo()), artifacts.resolve("external").resolve(backup.getNombre())) == -1);
            assertEquals(true, service.testRestore(1L, "prueba@example.test").get("ok"));
            assertEquals("verificado", backup.getVerificacionEstado());
            // Recuperación completa: descifrar copia antigua, incluir adjuntos, extraer y restaurar con pg_restore real.
            Path dump = artifacts.resolve("database.dump"), uploads = Files.createDirectories(artifacts.resolve("uploads/tareas"));
            com.sigaa.ops.BackupRecoveryTool.decryptDatabase(Path.of(backup.getRutaArchivo()), dump, new byte[32]);
            byte[] attachment = "%PDF-1.4\nFICTITIOUS_ATTACHMENT".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(uploads.resolve("fixture.pdf"), attachment);
            Path full = artifacts.resolve("full.enc"), extracted = artifacts.resolve("restored");
            com.sigaa.ops.BackupRecoveryTool.create(dump, artifacts.resolve("uploads"), full, new byte[32]);
            com.sigaa.ops.BackupRecoveryTool.extract(full, extracted, new byte[32]);
            assertArrayEquals(attachment, Files.readAllBytes(extracted.resolve("uploads/tareas/fixture.pdf")));
            try (var connection = postgres.dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE sigaa_full_fixture_restore");
                statement.execute("CREATE ROLE sigaa_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT");
                statement.execute("CREATE ROLE sigaa_backup LOGIN NOSUPERUSER CREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT");
            }
            var source = java.net.URI.create(postgres.dataSource.getUrl().split("\\?")[0].replaceFirst("^jdbc:", ""));
            runNative(postgres.tool("pg_restore"), "-h", source.getHost(), "-p", Integer.toString(source.getPort()),
                "-U", "sigaa_a05_test", "-d", "sigaa_full_fixture_restore", "--exit-on-error", "--no-owner", "--no-privileges", extracted.resolve("database.dump").toString());
            Path grants = Path.of(System.getProperty("sigaa.test.project.root", ".")).resolve("../../ops/postgres/grant-application-access.sql").normalize().toAbsolutePath();
            runNative(postgres.tool("psql"), "-h", source.getHost(), "-p", Integer.toString(source.getPort()),
                "-U", "sigaa_a05_test", "-d", "sigaa_full_fixture_restore", "-v", "ON_ERROR_STOP=1", "-f", grants.toString());
            try (var connection = java.sql.DriverManager.getConnection("jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/sigaa_full_fixture_restore", "sigaa_runtime", "TEST_ONLY_NOT_REAL");
                 var statement = connection.createStatement(); var result = statement.executeQuery("SELECT value FROM a06_fixture")) {
                assertTrue(result.next()); assertEquals(1, result.getInt(1));
                assertThrows(java.sql.SQLException.class, () -> statement.execute("CREATE TABLE forbidden_fixture (id integer)"));
            }
            try (var files = Files.walk(artifacts)) { assertFalse(files.anyMatch(file -> file.toString().endsWith(".tmp"))); }
            try (var connection = postgres.dataSource.getConnection(); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM pg_database WHERE datname LIKE 'sigaa_restore_%'")) {
                assertTrue(result.next()); assertEquals(0, result.getInt(1));
            }
        }
    }

    private static void runNative(Path executable, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>(); command.add(executable.toString()); command.addAll(java.util.List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", "TEST_ONLY_NOT_REAL");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try { assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue()); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
