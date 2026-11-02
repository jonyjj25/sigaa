package com.sigaa.service;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Backup;
import com.sigaa.dto.BackupOperationalStatusResponse;
import com.sigaa.dto.BackupResponse;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.BackupRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final byte[] MAGIC = "SIGAABK1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BackupRepository repository;
    private final AuditLogRepository auditRepository;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final Path backupDirectory;
    private final Path externalDirectory;
    private final String pgDumpCommand;
    private final String pgRestoreCommand;
    private final String encryptionKeyBase64;
    private final int retentionDays;
    private final int retentionCopies;
    private final boolean automaticEnabled;
    private final boolean restoreTestEnabled;
    private final String backupSchedule;
    private final String restoreTestSchedule;
    private final Clock clock;
    private final Duration processTimeout;
    private final Duration processStopTimeout;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Counter backupSuccess;
    private final Counter backupFailure;
    private final Counter restoreSuccess;
    private final Counter restoreFailure;
    private final AtomicLong lastBackupEpoch = new AtomicLong(0);
    private final AtomicLong lastRestoreEpoch = new AtomicLong(0);
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);

    public BackupService(
            BackupRepository repository,
            AuditLogRepository auditRepository,
            MeterRegistry meterRegistry,
            @Value("${app.backup.datasource.url:${spring.datasource.url}}") String datasourceUrl,
            @Value("${app.backup.datasource.username:${spring.datasource.username}}") String datasourceUsername,
            @Value("${app.backup.datasource.password:${spring.datasource.password}}") String datasourcePassword,
            @Value("${app.backup.directory}") String backupDirectory,
            @Value("${app.backup.external-directory:}") String externalDirectory,
            @Value("${app.backup.pg-dump-command}") String pgDumpCommand,
            @Value("${app.backup.pg-restore-command:pg_restore}") String pgRestoreCommand,
            @Value("${app.backup.encryption-key-base64:}") String encryptionKeyBase64,
            @Value("${app.backup.retention-days:30}") int retentionDays,
            @Value("${app.backup.retention-copies:30}") int retentionCopies,
            @Value("${app.backup.automatic-enabled:false}") boolean automaticEnabled,
            @Value("${app.backup.restore-test-enabled:false}") boolean restoreTestEnabled,
            @Value("${app.backup.schedule-cron:0 0 2 * * *}") String backupSchedule,
            @Value("${app.backup.restore-test-cron:0 0 4 * * SUN}") String restoreTestSchedule,
            @Value("${app.time-zone:America/Panama}") String timeZone,
            @Value("${app.backup.process-timeout:${BACKUP_PROCESS_TIMEOUT:PT30M}}") Duration processTimeout,
            @Value("${app.backup.process-stop-timeout:${BACKUP_PROCESS_STOP_TIMEOUT:PT5S}}") Duration processStopTimeout) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.backupDirectory = Path.of(backupDirectory).toAbsolutePath().normalize();
        this.externalDirectory = externalDirectory == null || externalDirectory.isBlank()
                ? null : Path.of(externalDirectory).toAbsolutePath().normalize();
        this.pgDumpCommand = pgDumpCommand;
        this.pgRestoreCommand = pgRestoreCommand;
        this.encryptionKeyBase64 = encryptionKeyBase64;
        this.retentionDays = Math.max(1, retentionDays);
        this.retentionCopies = Math.max(2, retentionCopies);
        this.automaticEnabled = automaticEnabled;
        this.restoreTestEnabled = restoreTestEnabled;
        this.backupSchedule = backupSchedule;
        this.restoreTestSchedule = restoreTestSchedule;
        this.clock = Clock.system(ZoneId.of(timeZone));
        if (processTimeout.isNegative() || processTimeout.isZero() || processTimeout.toMillis() == 0
                || processStopTimeout.isNegative() || processStopTimeout.isZero() || processStopTimeout.toMillis() == 0) {
            throw new IllegalArgumentException("Los límites de los procesos de respaldo deben ser positivos");
        }
        this.processTimeout = processTimeout;
        this.processStopTimeout = processStopTimeout;
        this.backupSuccess = meterRegistry.counter("sigaa_backup_total", "result", "success");
        this.backupFailure = meterRegistry.counter("sigaa_backup_total", "result", "failure");
        this.restoreSuccess = meterRegistry.counter("sigaa_restore_test_total", "result", "success");
        this.restoreFailure = meterRegistry.counter("sigaa_restore_test_total", "result", "failure");
        meterRegistry.gauge("sigaa_backup_last_success_epoch_seconds", lastBackupEpoch);
        meterRegistry.gauge("sigaa_restore_test_last_success_epoch_seconds", lastRestoreEpoch);
    }

    @PostConstruct
    void initializeOperationalMetrics() {
        updateAvailabilityMetrics(list());
    }

    public List<Backup> list() {
        List<Backup> backups = repository.findAllByOrderByFechaDesc();
        backups.forEach(this::reconcileAvailability);
        updateAvailabilityMetrics(backups);
        return backups;
    }

    public BackupResponse toResponse(Backup backup) {
        reconcileAvailability(backup);
        return new BackupResponse(
                backup.getId(), backup.getNombre(), backup.getTamanio(), backup.getEstado(),
                backup.getTipo(), backup.getFecha(), Boolean.TRUE.equals(backup.getCopiaExterna()),
                backup.getChecksumSha256(), backup.getVerificacionEstado(), backup.getVerificadoAt());
    }

    public BackupOperationalStatusResponse operationalStatus() {
        List<Backup> available = list().stream().filter(b -> "completado".equalsIgnoreCase(b.getEstado())).toList();
        LocalDateTime lastBackup = available.stream().map(Backup::getFecha).filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastRestore = available.stream().filter(b -> "verificado".equalsIgnoreCase(b.getVerificacionEstado()))
                .map(Backup::getVerificadoAt).filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        boolean encryptionReady = encryptionConfigured();
        boolean dumpReady = commandAvailable(pgDumpCommand);
        boolean restoreReady = commandAvailable(pgRestoreCommand);
        boolean externalReady = externalDirectory != null;
        return new BackupOperationalStatusResponse(
                encryptionReady && dumpReady && externalReady,
                encryptionReady,
                dumpReady,
                restoreReady,
                externalReady,
                automaticEnabled,
                restoreTestEnabled,
                backupInProgress.get(),
                retentionDays,
                retentionCopies,
                backupSchedule,
                restoreTestSchedule,
                lastBackup,
                lastRestore,
                repository.count());
    }

    public Backup create(String type, String actor) {
        if (externalDirectory == null) {
            throw new IllegalStateException("La copia externa de respaldos no está configurada");
        }
        if (!backupInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Ya existe un respaldo en progreso");
        }
        Path plainDump = null;
        Path encrypted = null;
        Path staged = null;
        Path externalCopyPath = null;
        Backup backup = new Backup();
        backup.setNombre("sigaa_" + LocalDateTime.now(clock).format(FILE_TIME));
        backup.setEstado("en_progreso");
        backup.setTipo(type);
        try {
            backup = repository.save(backup);
            SecretKey key = encryptionKey();
            Files.createDirectories(backupDirectory);
            requireCommand(pgDumpCommand, "pg_dump");
            DatabaseTarget target = databaseTarget();
            String stem = "sigaa_" + LocalDateTime.now(clock).format(FILE_TIME);
            plainDump = Files.createTempFile(backupDirectory, stem + "_", ".dump.tmp");
            encrypted = safeResolve(backupDirectory, plainDump.getFileName().toString().replaceFirst("\\.tmp$", ".enc"));
            staged = Files.createTempFile(backupDirectory, stem + "_", ".enc.tmp");
            externalCopyPath = safeResolve(externalDirectory, encrypted.getFileName().toString());

            runProcess(List.of(pgDumpCommand, "-h", target.host(), "-p", String.valueOf(target.port()),
                    "-U", datasourceUsername, "-F", "c", "--no-owner", "--no-privileges",
                    "--no-password", "-f", plainDump.toString(), target.database()), datasourcePassword, "pg_dump");
            validateDump(plainDump);
            encrypt(plainDump, staged, key);
            validateEncrypted(staged);
            moveFinal(staged, encrypted);
            validateEncrypted(encrypted);
            Files.deleteIfExists(plainDump);
            boolean externalCopy = copyExternal(encrypted);
            validateEncrypted(encrypted);
            validateEncrypted(externalCopyPath);
            String checksum = checksumSha256(encrypted);
            if (!checksum.equals(checksumSha256(externalCopyPath))) throw new BackupFailure("La copia externa no coincide con el respaldo generado");

            backup.setNombre(encrypted.getFileName().toString());
            backup.setRutaArchivo(encrypted.toString());
            backup.setTamanio(formatSize(Files.size(encrypted)));
            backup.setEstado("completado");
            backup.setTipo(type);
            backup.setCopiaExterna(externalCopy);
            backup.setChecksumSha256(checksum);
            backup.setVerificacionEstado("pendiente");
            Backup saved = repository.save(backup);
            backupSuccess.increment();
            lastBackupEpoch.set(Instant.now(clock).getEpochSecond());
            // Un fallo auxiliar no debe borrar un archivo ya confirmado como completo.
            try { audit(actor, "CREAR", saved.getId(), "Respaldo cifrado generado: " + saved.getNombre()); }
            catch (RuntimeException ex) { log.warn("No se pudo registrar la auditoría del respaldo {} ({})", saved.getId(), ex.getClass().getSimpleName()); }
            try { enforceRetention(); }
            catch (RuntimeException ex) { log.warn("No se pudo completar la retención de respaldos ({})", ex.getClass().getSimpleName()); }
            return saved;
        } catch (Exception ex) {
            backupFailure.increment();
            backup.setEstado("fallido"); backup.setVerificacionEstado("fallido"); backup.setCopiaExterna(false);
            try { repository.save(backup); }
            catch (RuntimeException failure) { log.error("No se pudo registrar el fallo del respaldo ({})", failure.getClass().getSimpleName()); }
            cleanFile(plainDump); cleanFile(staged); cleanFile(encrypted); cleanFile(externalCopyPath);
            String message = failureMessage(ex, "generar el respaldo");
            log.error("Respaldo {} fallido: {}", backup.getId(), message);
            throw new IllegalStateException(message);
        } finally {
            backupInProgress.set(false);
        }
    }

    public UrlResource download(Long id) {
        Backup backup = repository.findById(id).orElseThrow(NoSuchElementException::new);
        try {
            return new UrlResource(verifiedBackupPath(backup).toUri());
        } catch (IOException ex) {
            markUnavailable(backup);
            throw new NoSuchElementException("Archivo de respaldo no disponible");
        }
    }

    public Backup get(Long id) {
        Backup backup = repository.findById(id).orElseThrow(NoSuchElementException::new);
        reconcileAvailability(backup);
        return backup;
    }

    public void delete(Long id, String actor) {
        Backup backup = repository.findById(id).orElseThrow(NoSuchElementException::new);
        try {
            Path file = verifiedBackupPath(backup);
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("No se pudo eliminar el archivo físico del respaldo {} ({})", id, ex.getClass().getSimpleName());
        }
        try {
            deleteExternal(backup.getNombre());
        } catch (IOException ex) {
            log.warn("No se pudo eliminar la copia externa del respaldo {} ({})", id, ex.getClass().getSimpleName());
        }
        repository.delete(backup);
        audit(actor, "ELIMINAR", id, "Respaldo eliminado: " + backup.getNombre());
    }

    public Map<String, Object> testRestore(Long id, String actor) {
        Backup backup = repository.findById(id).orElseThrow(NoSuchElementException::new);
        Path decrypted = null;
        boolean databaseCreated = false;
        String temporaryDatabase = "sigaa_restore_" + UUID.randomUUID().toString().replace("-", "");
        try {
            requireCommand(pgRestoreCommand, "pg_restore");
            Path source = verifiedBackupPath(backup);
            decrypted = Files.createTempFile(backupDirectory, "sigaa-restore-", ".dump.tmp");
            decrypt(source, decrypted, encryptionKey());
            validateDump(decrypted);
            createTemporaryDatabase(temporaryDatabase);
            databaseCreated = true;
            DatabaseTarget target = databaseTarget();
            runProcess(List.of(pgRestoreCommand, "-h", target.host(), "-p", String.valueOf(target.port()),
                    "-U", datasourceUsername, "--exit-on-error", "--no-owner", "--no-privileges",
                    "--no-password", "-d", temporaryDatabase, decrypted.toString()), datasourcePassword, "pg_restore");
            verifyRestoredDatabase(temporaryDatabase);
            backup.setVerificacionEstado("verificado");
            backup.setVerificadoAt(LocalDateTime.now(clock));
            repository.save(backup);
            restoreSuccess.increment();
            lastRestoreEpoch.set(Instant.now(clock).getEpochSecond());
            try { audit(actor, "PROBAR_RESTAURACION", id, "Restauración verificada en una base temporal"); }
            catch (RuntimeException ex) { log.warn("No se pudo registrar la auditoría de restauración {} ({})", id, ex.getClass().getSimpleName()); }
            return Map.of("ok", true, "backupId", id, "verifiedAt", LocalDateTime.now(clock));
        } catch (Exception ex) {
            backup.setVerificacionEstado("fallido");
            backup.setVerificadoAt(LocalDateTime.now(clock));
            repository.save(backup);
            restoreFailure.increment();
            reconcileAvailability(backup);
            String message = failureMessage(ex, "probar la restauración");
            log.error("Restauración del respaldo {} fallida: {}", id, message);
            throw new IllegalStateException(message);
        } finally {
            if (databaseCreated) try { dropTemporaryDatabase(temporaryDatabase); } catch (Exception ex) {
                log.warn("No se pudo limpiar la base temporal de la restauración {} ({})", id, ex.getClass().getSimpleName());
            }
            cleanFile(decrypted);
        }
    }

    public void enforceRetention() {
        List<Backup> automatic = repository.findAllByOrderByFechaDesc().stream()
                .filter(item -> "automatico".equalsIgnoreCase(item.getTipo()))
                .filter(item -> "completado".equalsIgnoreCase(item.getEstado()))
                .sorted(Comparator.comparing(Backup::getFecha).reversed())
                .toList();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        for (int index = 0; index < automatic.size(); index++) {
            Backup backup = automatic.get(index);
            if (index >= retentionCopies || backup.getFecha().isBefore(cutoff)) {
                delete(backup.getId(), "system-retention");
            }
        }
    }

    private void encrypt(Path input, Path output, SecretKey key) throws IOException, GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try (var in = new BufferedInputStream(Files.newInputStream(input));
             var rawOut = new BufferedOutputStream(Files.newOutputStream(output))) {
            rawOut.write(MAGIC);
            rawOut.write(iv);
            try (var encryptedOut = new CipherOutputStream(rawOut, cipher)) {
                in.transferTo(encryptedOut);
            }
        }
    }

    private void decrypt(Path input, Path output, SecretKey key) throws IOException, GeneralSecurityException {
        try (var rawIn = new BufferedInputStream(Files.newInputStream(input))) {
            byte[] header = rawIn.readNBytes(MAGIC.length);
            if (!java.util.Arrays.equals(header, MAGIC)) throw new IOException("Formato de respaldo desconocido");
            byte[] iv = rawIn.readNBytes(IV_LENGTH);
            if (iv.length != IV_LENGTH) throw new IOException("Respaldo incompleto");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            try (var decryptedIn = new CipherInputStream(rawIn, cipher);
                 var out = new BufferedOutputStream(Files.newOutputStream(output))) {
                decryptedIn.transferTo(out);
            }
        }
    }

    private SecretKey encryptionKey() {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptionKeyBase64);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("BACKUP_ENCRYPTION_KEY_BASE64 debe contener 32 bytes en Base64");
        }
    }

    private boolean copyExternal(Path encrypted) throws IOException {
        if (externalDirectory == null) return false;
        Files.createDirectories(externalDirectory);
        Path staged = Files.createTempFile(externalDirectory, "sigaa-external-", ".tmp");
        try {
            Files.copy(encrypted, staged, StandardCopyOption.REPLACE_EXISTING);
            validateEncrypted(staged);
            moveFinal(staged, safeResolve(externalDirectory, encrypted.getFileName().toString()));
            return true;
        } finally { cleanFile(staged); }
    }

    private String checksumSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean encryptionConfigured() {
        try {
            return Base64.getDecoder().decode(encryptionKeyBase64).length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean commandAvailable(String command) {
        if (command == null || command.isBlank()) return false;
        return !(command.contains("/") || command.contains("\\")) || Files.isRegularFile(Path.of(command));
    }

    private void deleteExternal(String filename) throws IOException {
        if (externalDirectory != null) Files.deleteIfExists(safeResolve(externalDirectory, filename));
    }

    private Path verifiedBackupPath(Backup backup) throws IOException {
        if (!"completado".equalsIgnoreCase(backup.getEstado())) throw new BackupFailure("Archivo de respaldo no disponible");
        if (backup.getRutaArchivo() == null || backup.getRutaArchivo().isBlank()) throw new BackupFailure("Archivo de respaldo no disponible");
        Path file;
        try { file = Path.of(backup.getRutaArchivo()).toAbsolutePath().normalize(); }
        catch (java.nio.file.InvalidPathException ex) { throw new BackupFailure("La ubicación del respaldo no es válida"); }
        if (!file.startsWith(backupDirectory)) throw new BackupFailure("La ubicación del respaldo no es válida");
        validateEncrypted(file);
        if (!file.toRealPath().startsWith(backupDirectory.toRealPath())) throw new BackupFailure("La ubicación del respaldo no es válida");
        return file;
    }

    private Path safeResolve(Path base, String filename) throws IOException {
        Path result = base.resolve(filename).toAbsolutePath().normalize();
        if (Path.of(filename).isAbsolute() || Path.of(filename).getNameCount() != 1 || filename.contains("/")
                || filename.contains("\\") || !result.startsWith(base)) throw new BackupFailure("La ubicación del respaldo no es válida");
        return result;
    }

    private void runProcess(List<String> command, String password, String operation) throws IOException {
        Process process;
        try { process = startProcess(command, password); }
        catch (IOException ex) {
            if (ex instanceof AccessDeniedException || String.valueOf(ex.getMessage()).contains("error=5")
                    || String.valueOf(ex.getMessage()).contains("error=13"))
                throw new BackupFailure("No hay permisos para ejecutar " + operation);
            throw new BackupFailure("No se pudo iniciar " + operation + "; compruebe que la herramienta esté instalada");
        }
        ProcessOutput output = new ProcessOutput();
        Thread reader = Thread.ofVirtual().name("sigaa-backup-output").start(() -> output.read(process));
        Set<ProcessHandle> family = new LinkedHashSet<>();
        family.add(process.toHandle());
        boolean interrupted = false;
        try {
            process.getOutputStream().close();
            long deadline = System.nanoTime() + processTimeout.toNanos();
            while (true) {
                process.descendants().forEach(family::add);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 && process.isAlive()) throw new BackupFailure(operation + " superó el tiempo máximo permitido");
                if (process.waitFor(Math.max(0, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50))), TimeUnit.NANOSECONDS)) break;
            }
            reader.join(processStopTimeout.toMillis());
            if (reader.isAlive()) throw new BackupFailure("La salida de " + operation + " no finalizó dentro del tiempo permitido");
            if (output.failed) throw new BackupFailure("No se pudo leer la salida de " + operation);
            if (process.exitValue() != 0) {
                String diagnostic = output.text().toLowerCase(Locale.ROOT);
                if (diagnostic.contains("no space left") || diagnostic.contains("disk full"))
                    throw new BackupFailure("Espacio insuficiente al ejecutar " + operation);
                if (diagnostic.contains("permission denied") || diagnostic.contains("access is denied"))
                    throw new BackupFailure("Permisos insuficientes al ejecutar " + operation);
                throw new BackupFailure(operation + " terminó con código de salida " + process.exitValue());
            }
            // Descendants discovered while the command was running are always stopped
            // and verified in finally. Do not reject an otherwise successful command
            // here: Windows may transiently report its console helper as still alive.
        } catch (InterruptedException ex) {
            interrupted = true;
            throw new BackupFailure("La ejecución de " + operation + " fue interrumpida");
        } finally {
            try { stopProcessFamily(family); }
            finally {
                try { process.getInputStream().close(); } catch (IOException ignored) { }
                try { process.getErrorStream().close(); } catch (IOException ignored) { }
                try { process.getOutputStream().close(); } catch (IOException ignored) { }
                reader.interrupt();
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    // Punto de entrada único para procesos; sustituible en pruebas por comandos ficticios.
    Process startProcess(List<String> command, String password) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", password);
        return builder.start();
    }

    private void stopProcessFamily(Set<ProcessHandle> family) throws IOException {
        AtomicBoolean interrupted = new AtomicBoolean(Thread.interrupted());
        List<ProcessHandle> childrenFirst = new java.util.ArrayList<>(family);
        java.util.Collections.reverse(childrenFirst);
        try {
            childrenFirst.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
            awaitStopped(family, interrupted);
            childrenFirst.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            awaitStopped(family, interrupted);
            if (family.stream().anyMatch(ProcessHandle::isAlive)) throw new BackupFailure("No se pudo finalizar un proceso de respaldo o restauración");
        } finally { if (interrupted.get()) Thread.currentThread().interrupt(); }
    }

    private void awaitStopped(Set<ProcessHandle> family, AtomicBoolean interrupted) {
        long deadline = System.nanoTime() + processStopTimeout.toNanos();
        while (family.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try { Thread.sleep(20); }
            catch (InterruptedException ex) { interrupted.set(true); }
        }
    }

    private static class ProcessOutput {
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        volatile boolean failed;
        void read(Process process) {
            try (var stream = process.getInputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = stream.read(buffer)) != -1) {
                    synchronized (this) { captured.write(buffer, 0, Math.min(read, 32768 - captured.size())); }
                }
            } catch (IOException ex) { failed = true; }
        }
        synchronized String text() { return captured.toString(StandardCharsets.UTF_8); }
    }

    private void validateDump(Path file) throws IOException {
        validateFile(file, 5);
        try (var input = Files.newInputStream(file)) {
            if (!java.util.Arrays.equals(input.readNBytes(5), "PGDMP".getBytes(StandardCharsets.US_ASCII)))
                throw new BackupFailure("pg_dump no produjo un archivo de respaldo válido");
        }
    }

    private void validateEncrypted(Path file) throws IOException {
        validateFile(file, MAGIC.length + IV_LENGTH + 16 + 1);
        try (var input = Files.newInputStream(file)) {
            if (!java.util.Arrays.equals(input.readNBytes(MAGIC.length), MAGIC)) throw new BackupFailure("El archivo de respaldo cifrado no es válido");
        }
    }

    private void validateFile(Path file, long minimumSize) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new BackupFailure("Archivo de respaldo no disponible");
        if (!Files.isReadable(file)) throw new BackupFailure("No hay permisos para leer el archivo de respaldo");
        if (Files.size(file) < minimumSize) throw new BackupFailure("El archivo de respaldo está vacío o incompleto");
    }

    private void moveFinal(Path staged, Path destination) throws IOException {
        try { Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(staged, destination); }
    }

    private void reconcileAvailability(Backup backup) {
        if (!"completado".equalsIgnoreCase(backup.getEstado())) return;
        try { verifiedBackupPath(backup); }
        catch (IOException | java.nio.file.InvalidPathException ex) { markUnavailable(backup); }
        if ("completado".equalsIgnoreCase(backup.getEstado()) && Boolean.TRUE.equals(backup.getCopiaExterna())) {
            try {
                if (externalDirectory == null) throw new BackupFailure("La copia externa no está disponible");
                validateEncrypted(safeResolve(externalDirectory, backup.getNombre()));
            } catch (IOException | java.nio.file.InvalidPathException ex) {
                backup.setCopiaExterna(false); repository.save(backup);
            }
        }
    }

    private void markUnavailable(Backup backup) {
        if (!"completado".equalsIgnoreCase(backup.getEstado())) return;
        backup.setEstado("fallido"); backup.setVerificacionEstado("fallido");
        repository.save(backup);
        log.warn("Archivo del respaldo {} no disponible; se conserva su registro histórico", backup.getId());
    }

    private void updateAvailabilityMetrics(List<Backup> backups) {
        lastBackupEpoch.set(backups.stream().filter(b -> "completado".equalsIgnoreCase(b.getEstado()))
                .map(Backup::getFecha).filter(java.util.Objects::nonNull).mapToLong(date -> date.atZone(clock.getZone()).toEpochSecond()).max().orElse(0));
        lastRestoreEpoch.set(backups.stream().filter(b -> "completado".equalsIgnoreCase(b.getEstado()) && "verificado".equalsIgnoreCase(b.getVerificacionEstado()))
                .map(Backup::getVerificadoAt).filter(java.util.Objects::nonNull).mapToLong(date -> date.atZone(clock.getZone()).toEpochSecond()).max().orElse(0));
    }

    private void cleanFile(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); }
        catch (IOException ex) { log.warn("No se pudo limpiar un archivo temporal de respaldo ({})", ex.getClass().getSimpleName()); }
    }

    private String failureMessage(Exception ex, String operation) {
        if (ex instanceof BackupFailure) return ex.getMessage();
        if (ex instanceof AccessDeniedException) return "Permisos insuficientes para " + operation;
        if (ex instanceof NoSuchFileException) return "Archivo de respaldo no disponible";
        if (ex instanceof FileSystemException fs && fs.getReason() != null
                && (fs.getReason().toLowerCase(Locale.ROOT).contains("space")
                    || fs.getReason().toLowerCase(Locale.ROOT).contains("espacio"))) return "Espacio insuficiente para " + operation;
        if (ex instanceof IOException) return "Error de lectura o escritura al " + operation;
        if (ex instanceof IllegalStateException && String.valueOf(ex.getMessage()).startsWith("BACKUP_ENCRYPTION_KEY_BASE64"))
            return "La clave de cifrado de respaldos no está configurada correctamente";
        return "No fue posible " + operation;
    }

    private static class BackupFailure extends IOException {
        BackupFailure(String message) { super(message); }
    }

    private void requireCommand(String command, String name) throws IOException {
        if (command == null || command.isBlank()) throw new BackupFailure(name + " no está configurado");
        if ((command.contains("/") || command.contains("\\")) && !Files.isRegularFile(Path.of(command))) {
            throw new BackupFailure(name + " no se encontró en la configuración de herramientas");
        }
    }

    private DatabaseTarget databaseTarget() {
        if (!datasourceUrl.startsWith("jdbc:")) throw new IllegalStateException("DB_URL no es JDBC");
        URI uri = URI.create(datasourceUrl.substring("jdbc:".length()));
        String database = uri.getPath().replaceFirst("^/", "");
        if (!database.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException("Nombre de base de datos inválido");
        return new DatabaseTarget(uri.getHost(), uri.getPort() == -1 ? 5432 : uri.getPort(), database);
    }

    private void createTemporaryDatabase(String database) throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.setQueryTimeout(jdbcTimeoutSeconds());
            statement.execute("CREATE DATABASE \"" + database + "\"");
            statement.execute("REVOKE ALL ON DATABASE \"" + database + "\" FROM PUBLIC");
        }
    }

    private void dropTemporaryDatabase(String database) throws Exception {
        if (!database.matches("sigaa_restore_[a-f0-9]{32}")) return;
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.setQueryTimeout(jdbcTimeoutSeconds());
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + database + "'");
            statement.execute("DROP DATABASE IF EXISTS \"" + database + "\"");
        }
    }

    private Connection adminConnection() throws Exception {
        DatabaseTarget target = databaseTarget();
        return openConnection("jdbc:postgresql://" + target.host() + ":" + target.port() + "/" + target.database());
    }

    private void verifyRestoredDatabase(String database) throws Exception {
        DatabaseTarget target = databaseTarget();
        String url = "jdbc:postgresql://" + target.host() + ":" + target.port() + "/" + database;
        try (Connection connection = openConnection(url); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(jdbcTimeoutSeconds());
            try (ResultSet result = statement.executeQuery("""
                    SELECT to_regclass('public.usuarios') IS NOT NULL
                       AND to_regclass('public.roles') IS NOT NULL
                       AND to_regclass('public.usuario_roles') IS NOT NULL
                       AND to_regclass('public.permisos') IS NOT NULL
                    """)) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new BackupFailure("La base restaurada no contiene el esquema canónico de SIGAA");
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT count(*)
                    FROM usuarios u
                    JOIN usuario_roles ur ON ur.usuario_id = u.id
                    JOIN roles r ON r.id = ur.rol_id
                    WHERE u.activo = TRUE AND lower(trim(r.nombre)) = 'superadmin'
                    """)) {
                if (!result.next() || result.getLong(1) < 1) {
                    throw new BackupFailure("La base restaurada no contiene un Superadmin activo");
                }
            }
        }
    }

    Connection openConnection(String url) throws Exception {
        return DriverManager.getConnection(url + "?connectTimeout=" + jdbcTimeoutSeconds() + "&socketTimeout=" + jdbcTimeoutSeconds(),
                datasourceUsername, datasourcePassword);
    }

    private int jdbcTimeoutSeconds() { return (int) Math.min(Integer.MAX_VALUE, Math.max(1, processTimeout.toSeconds())); }

    private void audit(String actor, String action, Long id, String detail) {
        AuditLog audit = new AuditLog();
        audit.setUsuario(actor == null || actor.isBlank() ? "system" : actor);
        audit.setAccion(action);
        audit.setEntidad("backup");
        audit.setEntidadId(id);
        audit.setDetalle(detail);
        audit.setFecha(LocalDateTime.now(clock));
        auditRepository.save(audit);
    }

    private String formatSize(long bytes) {
        return bytes < 1024 * 1024 ? String.format("%.1f KB", bytes / 1024d)
                : String.format("%.1f MB", bytes / (1024d * 1024));
    }

    private record DatabaseTarget(String host, int port, String database) { }
}
