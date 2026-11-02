package com.sigaa.service;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.model.TareaAdjunto;
import com.sigaa.repository.TareaAdjuntoRepository;
import com.sigaa.util.FileContentValidator;
import com.sigaa.util.UploadStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TareaAdjuntoService {
    private static final Logger log = LoggerFactory.getLogger(TareaAdjuntoService.class);
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar");
    private final TareaAdjuntoRepository repository;
    private final Path directory;

    @org.springframework.beans.factory.annotation.Autowired
    public TareaAdjuntoService(TareaAdjuntoRepository repository) {
        this(repository, Path.of("uploads/tareas"));
    }

    TareaAdjuntoService(TareaAdjuntoRepository repository, Path directory) {
        this.repository = repository;
        this.directory = directory;
    }

    /** Existing attachments are retained by id; client paths and MIME types are never trusted. */
    @Transactional
    public void sincronizar(Long tareaId, List<Map<String, Object>> items, List<MultipartFile> files) {
        List<TareaAdjunto> existing = repository.findByTareaIdOrderById(tareaId);
        Set<Long> kept = new HashSet<>();
        Set<Integer> usedFiles = new HashSet<>();
        List<Path> stored = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        try {
            for (Map<String, Object> item : items) {
                if (item.get("id") != null) {
                    Long id = Long.valueOf(item.get("id").toString());
                    if (!kept.add(id) || existing.stream().noneMatch(a -> a.getId().equals(id)))
                        throw new IllegalArgumentException("El adjunto no pertenece a esta tarea.");
                    continue;
                }
                TareaAdjunto adjunto = new TareaAdjunto();
                adjunto.setTareaId(tareaId);
                if (item.get("archivoIndice") != null) {
                    int index = Integer.parseInt(item.get("archivoIndice").toString());
                    if (index < 0 || index >= files.size() || !usedFiles.add(index))
                        throw new IllegalArgumentException("Archivo adjunto inválido.");
                    MultipartFile file = files.get(index);
                    if (file.isEmpty() || file.getSize() > MAX_BYTES)
                        throw new IllegalArgumentException("Cada archivo debe contener datos y pesar como máximo 10 MB.");
                    String extension;
                    try { extension = FileContentValidator.validate(file, EXTENSIONS); }
                    catch (IOException ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
                    String original = Optional.ofNullable(file.getOriginalFilename()).orElse("archivo")
                        .replace('\\', '/');
                    String name = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
                    if (name.isBlank() || name.length() > 300)
                        throw new IllegalArgumentException("El nombre del archivo debe tener entre 1 y 300 caracteres.");
                    Path path = UploadStorage.store(directory, file);
                    stored.add(path);
                    adjunto.setNombre(name);
                    adjunto.setRutaArchivo(path.toString());
                    adjunto.setTipoContenido(switch (extension) {
                        case "pdf" -> "application/pdf";
                        case "png", "gif", "webp" -> "image/" + extension;
                        case "jpg", "jpeg" -> "image/jpeg";
                        default -> "application/octet-stream";
                    });
                    adjunto.setTamanoBytes(file.getSize());
                    adjunto.setTamano(String.format(Locale.ROOT, "%.1f KB", file.getSize() / 1024.0));
                } else {
                    // Backwards compatibility for imported references that contain no digital file.
                    adjunto.setNombre(Objects.toString(item.get("nombre"), ""));
                    adjunto.setTamano(Objects.toString(item.get("tamano"), ""));
                }
                repository.save(adjunto);
            }
            if (usedFiles.size() != files.size()) throw new IllegalArgumentException("Hay archivos sin asociar a la tarea.");
            for (TareaAdjunto old : existing) {
                if (!kept.contains(old.getId())) {
                    repository.delete(old);
                    if (old.getRutaArchivo() != null) removed.add(old.getRutaArchivo());
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) removed.forEach(TareaAdjuntoService.this::deleteStored);
                    else stored.forEach(path -> deleteStored(path.toString()));
                }
            });
        } catch (IOException | RuntimeException ex) {
            stored.forEach(path -> deleteStored(path.toString()));
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No fue posible almacenar los archivos. Inténtalo de nuevo.", ex);
        }
    }

    public boolean disponible(TareaAdjunto adjunto) {
        try { archivo(adjunto); return true; }
        catch (NoSuchElementException | IllegalArgumentException ex) { return false; }
    }

    public Path archivo(TareaAdjunto adjunto) {
        if (adjunto.getRutaArchivo() == null) throw new NoSuchElementException("Solo existe una referencia al archivo.");
        Path path = UploadStorage.resolveInside(directory, adjunto.getRutaArchivo());
        if (!Files.isRegularFile(path)) throw new NoSuchElementException("Archivo no disponible.");
        return path;
    }

    public void eliminarArchivosTrasCommit(Long tareaId) {
        List<String> paths = repository.findByTareaIdOrderById(tareaId).stream()
            .map(TareaAdjunto::getRutaArchivo).filter(Objects::nonNull).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { paths.forEach(TareaAdjuntoService.this::deleteStored); }
        });
    }

    private void deleteStored(String path) {
        try { Files.deleteIfExists(UploadStorage.resolveInside(directory, path)); }
        catch (IOException | IllegalArgumentException ex) { log.warn("No se pudo limpiar un adjunto de tarea: {}", LogDiagnostics.describe(ex)); }
    }
}
