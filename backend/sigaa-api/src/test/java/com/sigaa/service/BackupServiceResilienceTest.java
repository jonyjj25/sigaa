package com.sigaa.service;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.controller.BackupController;
import com.sigaa.model.Backup;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.BackupRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Archivos ficticios y procesos Java reales; nunca utiliza pg_dump ni una base existente. */
class BackupServiceResilienceTest {
    private Path root;
    private BackupRepository repository;
    private AuditLogRepository audit;
    private final Map<Long, Backup> committed = new HashMap<>();
    private TestService service;
    private boolean rejectCompletion;

    @BeforeEach void setup() throws Exception {
        root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "a06-fixture-");
        committed.clear(); rejectCompletion = false;
        repository = mock(BackupRepository.class); audit = mock(AuditLogRepository.class);
        when(repository.save(any(Backup.class))).thenAnswer(invocation -> {
            Backup b = invocation.getArgument(0);
            if (b.getId() == null) b.setId((long) committed.size() + 1);
            if (b.getFecha() == null) b.setFecha(LocalDateTime.now(ZoneId.of("America/Panama")));
            if (rejectCompletion && "completado".equals(b.getEstado())) throw new IllegalStateException("Fallo ficticio de persistencia");
            committed.put(b.getId(), copy(b)); return b;
        });
        when(repository.findById(anyLong())).thenAnswer(invocation -> java.util.Optional.ofNullable(committed.get(invocation.getArgument(0))).map(BackupServiceResilienceTest::copy));
        when(repository.findAllByOrderByFechaDesc()).thenAnswer(invocation -> committed.values().stream().map(BackupServiceResilienceTest::copy).toList());
        when(repository.count()).thenAnswer(invocation -> (long) committed.size());
        // El proceso ficticio arranca otra JVM; Windows puede superar 1,5 s en
        // ese arranque. Los modos bloqueados esperan 30 s y siguen agotando
        // este límite, sin cambiar el timeout del servicio de producción.
        Duration fixtureTimeout = System.getProperty("os.name").startsWith("Windows")
                ? Duration.ofSeconds(5) : Duration.ofMillis(1500);
        service = new TestService(repository, audit, root, fixtureTimeout);
    }

    @Test void successfulProcessPublishesMatchingValidCopiesBeforeCompletion() throws Exception {
        Backup result = service.create("manual", "prueba@example.test");
        assertEquals("completado", result.getEstado());
        assertTrue(Files.size(Path.of(result.getRutaArchivo())) > 36);
        assertTrue(Files.mismatch(Path.of(result.getRutaArchivo()), root.resolve("external").resolve(result.getNombre())) == -1);
        assertTrue(result.getCopiaExterna()); assertEquals(64, result.getChecksumSha256().length());
        assertNotNull(service.download(result.getId()));
        assertNoTemporaries(); assertAllProcessesStopped();
    }

    @Test void largeStdoutAndStderrDoNotBlockOrRequireUnboundedCapture() throws Exception {
        service.dumpMode = "flood";
        assertEquals("completado", service.create("manual", "prueba@example.test").getEstado());
        assertAllProcessesStopped(); assertNoTemporaries();
    }

    @ParameterizedTest @ValueSource(strings = {"hang", "fail", "missing", "empty"})
    void timeoutExitFailureAndAbsentOrEmptyFilesNeverProduceCompleteBackups(String mode) throws Exception {
        service.dumpMode = mode;
        var failure = assertThrows(IllegalStateException.class, () -> service.create("manual", "prueba@example.test"));
        assertEquals("fallido", committed.get(1L).getEstado());
        if (mode.equals("hang")) assertTrue(failure.getMessage().contains("tiempo máximo"));
        if (mode.equals("fail")) assertTrue(failure.getMessage().contains("código de salida 9"));
        assertFalse(failure.getMessage().contains("TEST_PRIVATE")); assertNull(failure.getCause());
        assertFilesEmpty(); assertAllProcessesStopped();
        assertFalse(service.operationalStatus().respaldoEnProgreso());
    }

    @ParameterizedTest @ValueSource(strings = {"child-hang", "child-fail"})
    void timeoutAndFailureTerminateDescendantsAsWellAsTheParent(String mode) throws Exception {
        service.dumpMode = mode;
        assertThrows(IllegalStateException.class, () -> service.create("manual", "prueba@example.test"));
        Path pid = root.resolve("child.pid"); assertTrue(Files.isRegularFile(pid));
        long childPid = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
        assertAllProcessesStopped(); assertFilesEmpty();
    }

    @Test void aMissingCommandIsReportedWithoutItsConfiguredPath() throws Exception {
        service.failStart = true;
        var failure = assertThrows(IllegalStateException.class, () -> service.create("manual", "prueba@example.test"));
        assertTrue(failure.getMessage().contains("No se pudo iniciar pg_dump"));
        assertFalse(failure.getMessage().contains(root.toString()));
        assertEquals("fallido", committed.get(1L).getEstado()); assertFilesEmpty();
    }

    @Test void anExternalWriteFailureCannotLeaveCompleteMetadataOrPartialFiles() throws Exception {
        Files.writeString(root.resolve("external"), "FICTICIO");
        assertThrows(IllegalStateException.class, () -> service.create("manual", "prueba@example.test"));
        assertEquals("fallido", committed.get(1L).getEstado());
        try (var files = Files.list(root.resolve("local"))) { assertEquals(0, files.count()); }
        assertEquals("FICTICIO", Files.readString(root.resolve("external")));
        assertAllProcessesStopped();
    }

    @Test void aFailureToPersistCompletionCleansBothCopiesAndKeepsAFailedRecord() throws Exception {
        rejectCompletion = true;
        assertThrows(IllegalStateException.class, () -> service.create("manual", "prueba@example.test"));
        assertEquals("fallido", committed.get(1L).getEstado()); assertFilesEmpty();
    }

    @Test void auditFailureDoesNotDeleteAnAlreadyCommittedBackup() throws Exception {
        when(audit.save(any())).thenThrow(new IllegalStateException("Fallo ficticio de auditoría"));
        Backup result = service.create("manual", "prueba@example.test");
        assertEquals("completado", committed.get(result.getId()).getEstado());
        assertTrue(service.download(result.getId()).exists()); assertNoTemporaries();
    }

    @Test void retentionFailureDoesNotDeleteAnAlreadyCommittedBackup() throws Exception {
        service.failRetention = true;
        Backup result = service.create("automatico", "prueba@example.test");
        assertEquals("completado", committed.get(result.getId()).getEstado());
        assertTrue(service.download(result.getId()).exists()); assertNoTemporaries();
    }

    @Test void consecutiveCreationsHaveDistinctNamesAndDoNotOverwritePreviousFiles() throws Exception {
        Backup first = service.create("manual", "prueba@example.test");
        Backup second = service.create("manual", "prueba@example.test");
        assertNotEquals(first.getNombre(), second.getNombre());
        assertTrue(service.download(first.getId()).exists()); assertTrue(service.download(second.getId()).exists());
    }

    @Test void deletionAfterCreationReturns404AndPreservesTheHistoricalRecord() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        LocalDateTime originalDate = backup.getFecha();
        Files.delete(Path.of(backup.getRutaArchivo()));
        var mvc = MockMvcBuilders.standaloneSetup(new BackupController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/backups/{id}/download", backup.getId())).andExpect(status().isNotFound());
        assertEquals("fallido", committed.get(backup.getId()).getEstado());
        assertEquals(originalDate, committed.get(backup.getId()).getFecha());
        verify(repository, never()).delete(any());
        assertEquals("fallido", service.list().getFirst().getEstado());
        assertNull(service.operationalStatus().ultimoRespaldoExitoso());
    }

    @Test void emptyOrMissingFilesCannotAppearAsAvailableInListingOrMetrics() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        Files.write(Path.of(backup.getRutaArchivo()), new byte[0]);
        assertEquals("fallido", service.toResponse(backup).estado());
        assertNull(service.operationalStatus().ultimoRespaldoExitoso());
        assertThrows(NoSuchElementException.class, () -> service.download(backup.getId()));
    }

    @Test void removalOfTheExternalCopyRemovesItsConfirmationWhileTheLocalCopyRemainsAvailable() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        Files.delete(root.resolve("external").resolve(backup.getNombre()));
        Backup current = service.list().getFirst();
        assertEquals("completado", current.getEstado()); assertFalse(current.getCopiaExterna());
        assertTrue(service.download(current.getId()).exists());
    }

    @Test void aStoredPathOutsideTheConfiguredDirectoryCannotBeDownloaded() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        Path outside = root.resolve("outside.enc"); Files.copy(Path.of(backup.getRutaArchivo()), outside);
        backup.setRutaArchivo(outside.toString()); repository.save(backup);
        assertThrows(NoSuchElementException.class, () -> service.download(backup.getId()));
        assertTrue(Files.exists(outside)); assertEquals("fallido", committed.get(backup.getId()).getEstado());
    }

    @ParameterizedTest @ValueSource(strings = {"hang", "fail", "child-hang"})
    void restorationFailuresCleanTheDumpAndStopAllProcesses(String mode) throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        service.restoreMode = mode;
        assertThrows(IllegalStateException.class, () -> service.testRestore(backup.getId(), "prueba@example.test"));
        assertEquals("fallido", committed.get(backup.getId()).getVerificacionEstado());
        assertEquals("completado", committed.get(backup.getId()).getEstado());
        assertTrue(service.sql.stream().anyMatch(query -> query.startsWith("DROP DATABASE")));
        assertNoTemporaries(); assertAllProcessesStopped();
        if (mode.equals("child-hang")) assertFalse(ProcessHandle.of(Long.parseLong(Files.readString(root.resolve("child.pid")))).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test void aSuccessfulRestorationRequiresSuccessfulExitAndVerification() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        assertEquals(true, service.testRestore(backup.getId(), "prueba@example.test").get("ok"));
        assertEquals("verificado", committed.get(backup.getId()).getVerificacionEstado());
        assertNoTemporaries(); assertAllProcessesStopped();
    }

    @ParameterizedTest @ValueSource(strings = {"missing-schema", "missing-superadmin"})
    void aSuccessfulProcessCannotVerifyAnIncompleteRestoredDatabase(String mode) throws Exception {
        Backup backup = service.create("manual", "prueba@example.test");
        service.restoreMode = mode;
        var failure = assertThrows(IllegalStateException.class,
                () -> service.testRestore(backup.getId(), "prueba@example.test"));
        assertTrue(failure.getMessage().contains(mode.equals("missing-schema") ? "esquema canónico" : "Superadmin activo"));
        assertEquals("fallido", committed.get(backup.getId()).getVerificacionEstado());
        assertNoTemporaries(); assertAllProcessesStopped();
    }

    @Test void aMissingRestoreFileDoesNotCreateOrDropAnyDatabase() throws Exception {
        Backup backup = service.create("manual", "prueba@example.test"); Files.delete(Path.of(backup.getRutaArchivo()));
        assertThrows(IllegalStateException.class, () -> service.testRestore(backup.getId(), "prueba@example.test"));
        assertTrue(service.sql.isEmpty()); assertEquals("fallido", committed.get(backup.getId()).getEstado());
    }

    private void assertFilesEmpty() throws Exception {
        for (String directory : List.of("local", "external")) {
            Path path = root.resolve(directory);
            if (Files.isDirectory(path)) try (var files = Files.list(path)) { assertEquals(0, files.count()); }
        }
    }
    private void assertNoTemporaries() throws Exception {
        try (var files = Files.walk(root)) { assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp"))); }
    }
    private void assertAllProcessesStopped() { assertTrue(service.processes.stream().noneMatch(Process::isAlive)); }
    private static Backup copy(Backup value) {
        Backup b = new Backup(); b.setId(value.getId()); b.setNombre(value.getNombre()); b.setTamanio(value.getTamanio());
        b.setEstado(value.getEstado()); b.setTipo(value.getTipo()); b.setFecha(value.getFecha()); b.setRutaArchivo(value.getRutaArchivo());
        b.setCopiaExterna(value.getCopiaExterna()); b.setChecksumSha256(value.getChecksumSha256());
        b.setVerificacionEstado(value.getVerificacionEstado()); b.setVerificadoAt(value.getVerificadoAt()); return b;
    }

    static class TestService extends BackupService {
        final Path root;
        final List<Process> processes = new ArrayList<>();
        final List<String> sql = new ArrayList<>();
        String dumpMode = "success", restoreMode = "success";
        boolean failStart, failRetention;
        TestService(BackupRepository repository, AuditLogRepository audit, Path root, Duration timeout) {
            super(repository, audit, new SimpleMeterRegistry(), "jdbc:postgresql://127.0.0.1:1/sigaa_test", "usuario_ficticio", "TEST_ONLY_NOT_REAL",
                    root.resolve("local").toString(), root.resolve("external").toString(), "pg_dump_fake", "pg_restore_fake",
                    Base64.getEncoder().encodeToString(new byte[32]), 30, 30, false, false, "0 0 2 * * *", "0 0 4 * * SUN", "America/Panama",
                    timeout, Duration.ofMillis(250));
            this.root = root;
        }
        @Override Process startProcess(List<String> command, String password) throws IOException {
            if (failStart) throw new IOException("CreateProcess error=2, TEST_PRIVATE");
            boolean dump = command.get(0).equals("pg_dump_fake");
            String file = dump ? command.get(command.indexOf("-f") + 1) : command.getLast();
            String mode = dump ? dumpMode : restoreMode;
            Path childPid = root.resolve("child.pid");
            Files.deleteIfExists(childPid);
            Process p = new ProcessBuilder(fixtureCommand(mode, file, childPid.toString()))
                    .redirectErrorStream(true).start(); processes.add(p);
            if (mode.startsWith("child-")) {
                // El timeout mide el comando bloqueado, no el arranque de dos JVM de prueba.
                long readyDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (!Files.isRegularFile(childPid) && p.isAlive() && System.nanoTime() < readyDeadline) {
                    try { Thread.sleep(10); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); p.destroyForcibly(); throw new IOException("Fixture interrumpida", ex); }
                }
                if (!Files.isRegularFile(childPid)) {
                    p.destroyForcibly(); throw new IOException("La fixture no inició su proceso hijo");
                }
            }
            return p;
        }
        @Override Connection openConnection(String url) throws Exception {
            Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute(anyString())).thenAnswer(invocation -> { sql.add(invocation.getArgument(0)); return false; });
            when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
                ResultSet result = mock(ResultSet.class);
                when(result.next()).thenReturn(true, false);
                when(result.getBoolean(1)).thenReturn(!restoreMode.equals("missing-schema"));
                when(result.getLong(1)).thenReturn(restoreMode.equals("missing-superadmin") ? 0L : 1L);
                return result;
            });
            return connection;
        }
        @Override public void enforceRetention() { if (failRetention) throw new IllegalStateException("Fallo ficticio de retención"); super.enforceRetention(); }
    }

    private static List<String> fixtureCommand(String mode, String file, String pidFile) {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        return List.of(java, "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), ProcessFixture.class.getName(), mode, file, pidFile);
    }

    public static class ProcessFixture {
        public static void main(String[] args) throws Exception {
            String mode = args[0];
            if (mode.equals("child")) { Thread.sleep(30000); return; }
            Path file = Path.of(args[1]);
            if (mode.equals("missing")) { Files.deleteIfExists(file); return; }
            if (mode.equals("empty")) { Files.write(file, new byte[0]); return; }
            if (mode.equals("fail")) { System.err.println("TEST_PRIVATE: fallo ficticio"); System.exit(9); }
            if (mode.equals("flood")) for (int i = 0; i < 256; i++) { System.out.write(new byte[8192]); System.err.write(new byte[8192]); }
            if (mode.startsWith("child-")) {
                Process child = new ProcessBuilder(fixtureCommand("child", args[1], args[2])).inheritIO().start();
                Files.writeString(Path.of(args[2]), Long.toString(child.pid()));
                Thread.sleep(400);
                if (mode.equals("child-fail")) System.exit(9);
            }
            if (mode.equals("hang") || mode.equals("child-hang")) Thread.sleep(30000);
            Files.write(file, "PGDMP_FICTITIOUS_TEST_ONLY".getBytes(StandardCharsets.US_ASCII));
        }
    }
}
