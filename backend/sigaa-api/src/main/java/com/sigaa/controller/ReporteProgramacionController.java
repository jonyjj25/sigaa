package com.sigaa.controller;

import com.sigaa.dto.ReporteProgramacionRequest;
import com.sigaa.model.AuditLog;
import com.sigaa.model.ReporteProgramacion;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ReporteProgramacionRepository;
import com.sigaa.repository.ReporteRepository;
import com.sigaa.util.FileContentValidator;
import com.sigaa.util.UploadStorage;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reportes/programaciones")
@PreAuthorize("hasRole('superadmin') or (hasAnyRole('Director','Directora') and hasAuthority('Reporte:Configurar'))")
public class ReporteProgramacionController {
    private final Path uploadDir = Paths.get("uploads/reportes");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "txt", "csv", "zip", "rar");

    private final ReporteProgramacionRepository repository;
    private final AuditLogRepository auditRepository;
    private final ReporteRepository reportes;

    public ReporteProgramacionController(ReporteProgramacionRepository repository, AuditLogRepository auditRepository,
                                         ReporteRepository reportes) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.reportes = reportes;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toMap).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody ReporteProgramacionRequest request,
                                                      Authentication authentication) {
        ReporteProgramacion programacion = new ReporteProgramacion();
        programacion.setNombre(request.nombre().trim());
        programacion.setModulo(request.modulo());
        programacion.setTipo(request.tipo());
        programacion.setFormato(request.formato());
        programacion.setPeriodicidad(request.periodicidad());
        programacion.setFechaInicio(request.fechaInicio());
        programacion.setHora(request.hora());
        programacion.setDestinatarios(request.destinatarios() == null ? "" : request.destinatarios().trim());
        programacion.setCreadoPor(authentication == null ? "sistema" : authentication.getName());
        programacion.setProximaEjecucion(LocalDateTime.of(request.fechaInicio(), request.hora()));
        ReporteProgramacion saved = repository.save(programacion);
        audit(authentication, "PROGRAMAR", saved.getId(), "Reporte periódico creado: " + saved.getNombre());
        return ResponseEntity.ok(toMap(saved));
    }

    @PatchMapping("/{id}/estado")
    @Transactional
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body,
                                                              Authentication authentication) {
        Object activo = body.get("activo");
        if (!(activo instanceof Boolean value)) return ResponseEntity.badRequest().build();
        return repository.findById(id).map(programacion -> {
            programacion.setActivo(value);
            programacion.setUpdatedAt(LocalDateTime.now());
            repository.save(programacion);
            audit(authentication, value ? "REANUDAR" : "PAUSAR", id, "Programación de reporte actualizada");
            return ResponseEntity.ok(toMap(programacion));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{id}/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> subirArchivos(@PathVariable Long id,
                                            @RequestParam("files") List<MultipartFile> files,
                                            Authentication authentication) {
        Optional<ReporteProgramacion> optional = repository.findByIdForUpdate(id);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        ReporteProgramacion programacion = optional.get();
        List<String> rutas = new ArrayList<>();
        if (!programacion.getArchivosRuta().isBlank()) rutas.addAll(Arrays.asList(programacion.getArchivosRuta().split(";")));
        try {
            for (MultipartFile file : files) {
                FileContentValidator.validate(file, ALLOWED_EXTENSIONS);
            }
            for (MultipartFile file : files) rutas.add(UploadStorage.storeTransactional(uploadDir, file, null).toString());
        } catch (IOException exception) {
            // Devolver 400 no lanza una excepción: hay que revertir las copias ya realizadas.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        programacion.setArchivosRuta(String.join(";", rutas));
        programacion.setUpdatedAt(LocalDateTime.now());
        repository.save(programacion);
        audit(authentication, "SUBIR_ARCHIVO", id, files.size() + " archivo(s) añadidos a la programación");
        return ResponseEntity.ok(toMap(programacion));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        Optional<ReporteProgramacion> optional = repository.findByIdForUpdate(id);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        audit(authentication, "ELIMINAR_PROGRAMACION", id, "Programación de reporte eliminada");
        repository.deleteById(id);
        repository.flush();
        for (String ruta : optional.get().getArchivosRuta().split(";")) {
            if (!ruta.isBlank() && !repository.referenciaArchivo(ruta) && !reportes.referenciaArchivo(ruta)) {
                UploadStorage.deleteAfterCommit(uploadDir, ruta);
            }
        }
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(ReporteProgramacion value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("nombre", value.getNombre());
        result.put("modulo", value.getModulo());
        result.put("tipo", value.getTipo());
        result.put("formato", value.getFormato());
        result.put("periodicidad", value.getPeriodicidad());
        result.put("fechaInicio", value.getFechaInicio());
        result.put("hora", value.getHora());
        result.put("destinatarios", value.getDestinatarios());
        result.put("activo", value.isActivo());
        result.put("proximaEjecucion", value.getProximaEjecucion());
        result.put("ultimaEjecucion", value.getUltimaEjecucion());
        result.put("ultimoEstado", value.getUltimoEstado());
        result.put("ultimoError", value.getUltimoError());
        return result;
    }

    private void audit(Authentication authentication, String accion, Long id, String detalle) {
        AuditLog log = new AuditLog();
        log.setUsuario(authentication == null ? "sistema" : authentication.getName());
        log.setAccion(accion);
        log.setEntidad("reporte_programacion");
        log.setEntidadId(id);
        log.setDetalle(detalle);
        auditRepository.save(log);
    }
}
