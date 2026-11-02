package com.sigaa.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Centralizes safe storage of user-provided files. */
public final class UploadStorage {
    private static final Logger log = LoggerFactory.getLogger(UploadStorage.class);
    private UploadStorage() { }

    public static Path store(Path directory, MultipartFile file) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = Path.of(original).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..")) safeName = "file";
        Path target = root.resolve(UUID.randomUUID() + "_" + safeName).normalize();
        if (!target.startsWith(root)) throw new IOException("Nombre de archivo inválido");
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
        return target;
    }

    /** El original sigue disponible hasta el commit; el nuevo se limpia al revertir. */
    public static Path storeTransactional(Path directory, MultipartFile file, String previous) throws IOException {
        requireSynchronization();
        Path stored = store(directory, file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) deleteStored(directory, previous);
                else deleteStored(directory, stored.toString());
            }
        });
        return stored;
    }

    public static void deleteAfterCommit(Path directory, String path) {
        if (path == null) return;
        requireSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteStored(directory, path); }
        });
    }

    private static void requireSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("El cambio de archivo requiere una transacción");
        }
    }

    private static void deleteStored(Path directory, String path) {
        if (path == null) return;
        try { Files.deleteIfExists(resolveInside(directory, path)); }
        catch (IOException | IllegalArgumentException ex) { log.warn("No se pudo limpiar un archivo almacenado: {}", LogDiagnostics.describe(ex)); }
    }

    public static Path resolveInside(Path directory, String storedPath) {
        Path root = directory.toAbsolutePath().normalize();
        Path candidate = Path.of(storedPath).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("Ruta de archivo inválida");
        return candidate;
    }
}
