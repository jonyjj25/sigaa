package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.NotaPdf;
import com.sigaa.util.FileContentValidator;
import com.sigaa.util.UploadStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Almacena de forma segura versiones inmutables de los PDF de notas. */
@Service
public class NotaPdfStorageService {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> PDF_ONLY = Set.of("pdf");
    private final Path directory;

    public NotaPdfStorageService() {
        this(Path.of("uploads/notas"));
    }

    NotaPdfStorageService(Path directory) {
        this.directory = directory;
    }

    public StoredPdf guardar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar un PDF.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El PDF no puede superar los 10 MB.");
        }
        final String extension;
        try {
            extension = FileContentValidator.validate(file, PDF_ONLY);
        } catch (IOException exception) {
            throw new IllegalArgumentException("El archivo adjunto debe ser un PDF válido.", exception);
        }
        if (!"pdf".equals(extension)) {
            throw new IllegalArgumentException("El archivo adjunto debe ser un PDF.");
        }

        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("nota.pdf").replace('\\', '/');
        String nombre = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (nombre.isBlank() || nombre.length() > 300) {
            throw new IllegalArgumentException("El nombre del PDF debe tener entre 1 y 300 caracteres.");
        }
        try {
            Path stored = UploadStorage.store(directory, file);
            limpiarAlRevertir(stored);
            return new StoredPdf(nombre, stored.toString(), "application/pdf", file.getSize());
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo almacenar el PDF de la nota.", exception);
        }
    }

    public Path archivo(Nota nota) {
        if (nota.getPdfRuta() == null || nota.getPdfRuta().isBlank()) {
            throw new NoSuchElementException("El PDF de la nota no está disponible.");
        }
        Path path = UploadStorage.resolveInside(directory, nota.getPdfRuta());
        if (!Files.isRegularFile(path)) throw new NoSuchElementException("El PDF de la nota no está disponible.");
        return path;
    }

    public Path archivo(NotaPdf pdf) {
        if (pdf.getRutaInterna() == null || pdf.getRutaInterna().isBlank()) {
            throw new NoSuchElementException("El PDF solicitado no está disponible.");
        }
        Path path = UploadStorage.resolveInside(directory, pdf.getRutaInterna());
        if (!Files.isRegularFile(path)) throw new NoSuchElementException("El PDF solicitado no está disponible.");
        return path;
    }

    public void eliminarTrasConfirmar(String ruta) {
        if (ruta == null || ruta.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eliminar(ruta);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) eliminar(ruta);
            }
        });
    }

    private void limpiarAlRevertir(Path stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) eliminar(stored.toString());
            }
        });
    }

    private void eliminar(String ruta) {
        try { Files.deleteIfExists(UploadStorage.resolveInside(directory, ruta)); }
        catch (IOException | IllegalArgumentException ignored) { /* The record remains auditable even if cleanup fails. */ }
    }

    public record StoredPdf(String nombre, String ruta, String tipoMime, long tamanoBytes) { }
}
