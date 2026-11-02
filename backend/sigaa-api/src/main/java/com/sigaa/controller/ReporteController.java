package com.sigaa.controller;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Reporte;
import com.sigaa.dto.ReporteGeneracionRequest;
import com.sigaa.dto.ReporteRequest;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ReporteRepository;
import com.sigaa.repository.ReporteProgramacionRepository;
import com.sigaa.service.ReporteGenerationService;
import com.sigaa.util.UploadStorage;
import com.sigaa.util.FileContentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {
    private static final Logger log = LoggerFactory.getLogger(ReporteController.class);

    private final ReporteRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final ReporteGenerationService generationService;
    private final ReporteProgramacionRepository programaciones;

    public ReporteController(ReporteRepository repository, AuditLogRepository auditLogRepository,
                             ReporteGenerationService generationService, ReporteProgramacionRepository programaciones) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.generationService = generationService;
        this.programaciones = programaciones;
        try { Files.createDirectories(uploadDir); } catch (IOException ignored) {}
    }

    private final Path uploadDir = Paths.get("uploads/reportes");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif", "txt", "csv", "zip", "rar"
    );

    @GetMapping
    @PreAuthorize("hasAuthority('Reporte:Ver')")
    public List<Map<String, Object>> listar() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long usuarioId = com.sigaa.service.InstitutionalRoles.scopedSubdirector(auth) ? generationService.usuarioActualId() : null;
        return repository.findAllByOrderByFechaDesc().stream()
            .filter(r -> !com.sigaa.service.InstitutionalRoles.scopedSubdirector(auth)
                || (usuarioId != null && usuarioId.equals(r.getCreadoPorUsuarioId())))
            .map(this::toMap).toList();
    }

    @GetMapping("/plantillas")
    @PreAuthorize("hasAuthority('Reporte:Ver')")
    public List<Map<String, String>> plantillas() {
        return generationService.plantillas();
    }

    @PostMapping("/generar")
    @Transactional
    @PreAuthorize("hasAuthority('Reporte:Crear')")
    public ResponseEntity<Map<String, Object>> generar(@Valid @RequestBody ReporteGeneracionRequest request) {
        String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        Reporte saved = generationService.generar(request, usuario);
        registrarAudit("GENERAR", saved.getId(), "Informe generado desde plantilla: " + request.plantilla());
        return ResponseEntity.ok(toMap(saved));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Reporte:Ver')")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable Long id) {
        return repository.findById(id)
            .filter(this::puedeAcceder)
            .map(r -> ResponseEntity.ok(toMap(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/exportar")
    @PreAuthorize("hasAuthority('Reporte:Ver')")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id,
                                            @RequestParam(defaultValue = "CSV") String formato) {
        if (!"PDF".equalsIgnoreCase(formato) && !"CSV".equalsIgnoreCase(formato) && !"Excel".equalsIgnoreCase(formato)) {
            return ResponseEntity.badRequest().build();
        }
        return repository.findById(id).map(reporte -> {
            exigirAcceso(reporte);
            ReporteGenerationService.Exportacion archivo = generationService.exportar(reporte, formato);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(archivo.mediaType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.archivo() + "\"")
                    .body(archivo.contenido());
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('Reporte:Crear')")
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody ReporteRequest body) {
        Reporte r = new Reporte();
        applyBody(r, body);
        r.setCreadoPorUsuarioId(generationService.usuarioActualId());
        if (r.getFecha() == null) r.setFecha(LocalDate.now());
        Reporte saved = repository.save(r);
        registrarAudit("CREAR", saved.getId(), "Reporte \"" + saved.getNombre() + "\" creado");
        return ResponseEntity.ok(toMap(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('Reporte:Editar')")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @Valid @RequestBody ReporteRequest body) {
        return repository.findByIdForUpdate(id).map(r -> {
            exigirAcceso(r);
            List<String> cambios = detectarCambios(r, body);
            applyBody(r, body);
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);
            String detalle = cambios.isEmpty() ? "Reporte actualizado" : "Cambios: " + String.join(", ", cambios);
            registrarAudit("ACTUALIZAR", id, detalle);
            return ResponseEntity.ok(toMap(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('Reporte:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Optional<Reporte> opt = repository.findByIdForUpdate(id);
        if (opt.isPresent()) {
            Reporte r = opt.get();
            exigirAcceso(r);
            repository.deleteById(id);
            registrarAudit("ELIMINAR", id, "Reporte \"" + r.getNombre() + "\" eliminado");
            repository.flush();
            if (r.getArchivosRuta() != null && !r.getArchivosRuta().isBlank()) {
                for (String ruta : r.getArchivosRuta().split(";")) {
                    // Las programaciones y sus instantáneas pueden compartir el mismo adjunto.
                    if (!ruta.isBlank() && !repository.referenciaArchivo(ruta) && !programaciones.referenciaArchivo(ruta)) {
                        UploadStorage.deleteAfterCommit(uploadDir, ruta);
                    }
                }
            }
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /* ---- Archivos ---- */

    @PostMapping(value = "/{id}/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    @PreAuthorize("hasAuthority('Reporte:Subir archivos')")
    public ResponseEntity<Map<String, Object>> subirArchivos(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files) {
        Optional<Reporte> opt = repository.findByIdForUpdate(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Reporte r = opt.get();
        exigirAcceso(r);
        List<String> rutas = new ArrayList<>();
        if (r.getArchivosRuta() != null && !r.getArchivosRuta().isEmpty()) {
            rutas.addAll(Arrays.asList(r.getArchivosRuta().split(";")));
        }
        List<String> errores = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
            String ext = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) ext = originalName.substring(dot + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                errores.add(originalName + ": extensión no permitida (." + ext + ")");
                continue;
            }
            try {
                FileContentValidator.validate(file, ALLOWED_EXTENSIONS);
                Path targetPath = UploadStorage.storeTransactional(uploadDir, file, null);
                rutas.add(targetPath.toString());
            } catch (IOException e) {
                log.warn("Archivo de reporte rechazado para reporte id={}, causa={}", id, e.getClass().getSimpleName());
                errores.add(originalName + ": No se pudo validar o almacenar el archivo.");
            }
        }
        r.setArchivosRuta(String.join(";", rutas));
        r.setUpdatedAt(LocalDateTime.now());
        repository.save(r);
        registrarAudit("SUBIR_ARCHIVO", id, files.size() + " archivo(s) adjunto(s)");
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(r.getId()));
        agregarReferenciasArchivos(m, r);
        if (!errores.isEmpty()) {
            m.put("errores", errores);
        }
        return ResponseEntity.ok(m);
    }

    @GetMapping("/{id}/descargar-archivo/{archivoIndex}")
    @PreAuthorize("hasAuthority('Reporte:Ver')")
    public ResponseEntity<UrlResource> descargarArchivo(@PathVariable Long id, @PathVariable int archivoIndex) {
        Optional<Reporte> opt = repository.findById(id);
        opt.ifPresent(this::exigirAcceso);
        if (opt.isEmpty() || opt.get().getArchivosRuta() == null || opt.get().getArchivosRuta().isEmpty())
            return ResponseEntity.notFound().build();
        List<String> rutas = Arrays.asList(opt.get().getArchivosRuta().split(";"));
        if (archivoIndex < 0 || archivoIndex >= rutas.size()) return ResponseEntity.notFound().build();
        try {
            Path filePath = UploadStorage.resolveInside(uploadDir, rutas.get(archivoIndex));
            UrlResource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                MediaType mediaType = detectMediaType(rutas.get(archivoIndex));
                String filename = filePath.getFileName().toString();
                int underscore = filename.indexOf('_');
                if (underscore >= 0) filename = filename.substring(underscore + 1);
                return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                    .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType detectMediaType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".txt") || lower.endsWith(".csv")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return MediaType.parseMediaType("application/msword");
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.ms-excel");
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean puedeAcceder(Reporte reporte) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return !com.sigaa.service.InstitutionalRoles.scopedSubdirector(auth)
            || (reporte.getCreadoPorUsuarioId() != null
                && reporte.getCreadoPorUsuarioId().equals(generationService.usuarioActualId()));
    }

    private void exigirAcceso(Reporte reporte) {
        if (!puedeAcceder(reporte)) throw new org.springframework.security.access.AccessDeniedException("Sin acceso a este reporte");
    }

    private void applyBody(Reporte r, ReporteRequest body) {
        r.setNombre(body.getNombre().trim());
        r.setModulo(body.getModulo().trim());
        r.setTipo(body.getTipo());
        r.setFecha(body.getFecha());
        r.setGeneradoPor(body.getGeneradoPor() == null ? "" : body.getGeneradoPor().trim());
        r.setFormato(body.getFormato());
        r.setDescripcion(body.getDescripcion() == null ? "" : body.getDescripcion().trim());
    }

    private Map<String, Object> toMap(Reporte r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("nombre", r.getNombre());
        m.put("modulo", r.getModulo());
        m.put("tipo", r.getTipo());
        m.put("fecha", r.getFecha().toString());
        m.put("generadoPor", r.getGeneradoPor());
        m.put("formato", r.getFormato());
        m.put("descripcion", r.getDescripcion());
        agregarReferenciasArchivos(m, r);
        m.put("generadoDesdePlantilla", r.getContenido() != null && !r.getContenido().isBlank());
        return m;
    }

    private void agregarReferenciasArchivos(Map<String, Object> respuesta, Reporte reporte) {
        List<String> enlaces = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        if (reporte.getArchivosRuta() != null && !reporte.getArchivosRuta().isBlank()) {
            String[] rutas = reporte.getArchivosRuta().split(";");
            for (int indice = 0; indice < rutas.length; indice++) {
                enlaces.add("/api/reportes/" + reporte.getId() + "/descargar-archivo/" + indice);
                String nombre = rutas[indice].replace('\\', '/');
                nombre = nombre.substring(nombre.lastIndexOf('/') + 1);
                int separador = nombre.indexOf('_');
                nombres.add(separador >= 0 ? nombre.substring(separador + 1) : nombre);
            }
        }
        respuesta.put("archivosRuta", String.join(";", enlaces));
        respuesta.put("archivosNombres", nombres);
    }

    private void registrarAudit(String accion, Long reporteId, String detalle) {
        String usuario = "sistema";
        try {
            usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {}
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad("reporte");
        log.setEntidadId(reporteId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }

    private List<String> detectarCambios(Reporte actual, ReporteRequest body) {
        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(actual.getNombre(), body.getNombre().trim()))
            cambios.add("nombre");
        if (!Objects.equals(actual.getModulo(), body.getModulo().trim()))
            cambios.add("modulo");
        if (!Objects.equals(actual.getTipo(), body.getTipo()))
            cambios.add("tipo");
        if (!Objects.equals(actual.getFecha(), body.getFecha()))
            cambios.add("fecha");
        if (!Objects.equals(actual.getGeneradoPor(), body.getGeneradoPor() == null ? "" : body.getGeneradoPor().trim()))
            cambios.add("generadoPor");
        if (!Objects.equals(actual.getFormato(), body.getFormato()))
            cambios.add("formato");
        if (!Objects.equals(actual.getDescripcion(), body.getDescripcion() == null ? "" : body.getDescripcion().trim()))
            cambios.add("descripcion");
        return cambios;
    }
}
