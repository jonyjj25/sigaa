package com.sigaa.controller;

import com.sigaa.dto.EstadoRequest;
import com.sigaa.dto.TareaAsignacionRequest;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.UserNotificationEmailService;
import com.sigaa.service.InternalNotificationService;
import com.sigaa.service.TareaAccessService;
import com.sigaa.service.ParticipantePolicy;
import com.sigaa.service.TareaAdjuntoService;
import com.sigaa.service.TareaVencimientoService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tareas")
public class TareaController {

    private final TareaRepository tareaRepository;
    private final TareaChecklistRepository checklistRepository;
    private final TareaAdjuntoRepository adjuntoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ModuloRepository moduloRepository;
    private final UserNotificationEmailService notificationEmailService;
    private final InternalNotificationService internalNotificationService;
    private final TareaAccessService tareaAccessService;
    private final TareaAdjuntoService tareaAdjuntoService;
    private final AuditLogRepository auditLogRepository;
    private final TareaVencimientoService tareaVencimientoService;

    public TareaController(TareaRepository tareaRepository,
                           TareaChecklistRepository checklistRepository,
                           TareaAdjuntoRepository adjuntoRepository,
                           UsuarioRepository usuarioRepository,
                           ModuloRepository moduloRepository,
                           UserNotificationEmailService notificationEmailService, InternalNotificationService internalNotificationService,
                           TareaAccessService tareaAccessService, TareaAdjuntoService tareaAdjuntoService,
                           AuditLogRepository auditLogRepository,
                           TareaVencimientoService tareaVencimientoService) {
        this.tareaRepository = tareaRepository;
        this.checklistRepository = checklistRepository;
        this.adjuntoRepository = adjuntoRepository;
        this.usuarioRepository = usuarioRepository;
        this.moduloRepository = moduloRepository;
        this.notificationEmailService = notificationEmailService;
        this.internalNotificationService = internalNotificationService;
        this.tareaAccessService = tareaAccessService;
        this.tareaAdjuntoService = tareaAdjuntoService;
        this.auditLogRepository = auditLogRepository;
        this.tareaVencimientoService = tareaVencimientoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Tareas:Ver')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        tareaVencimientoService.actualizarVencidas();
        return tareaAccessService.tareasVisiblesActual().stream().map(this::toFullTarea).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Tareas:Ver')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable Long id) {
        return tareaRepository.findById(id)
            .filter(tareaAccessService::puedeVer)
            .map(t -> ResponseEntity.ok(toFullTarea(t)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('Tareas:Ver')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MovimientoTarea>> historial(@PathVariable Long id) {
        return tareaRepository.findById(id)
            .filter(tareaAccessService::puedeVer)
            .map(t -> ResponseEntity.ok(auditLogRepository.findByEntidadAndEntidadIdOrderByFechaDesc("tarea", id)
                .stream().sorted(Comparator.comparing(AuditLog::getFecha).thenComparing(AuditLog::getId))
                .map(log -> new MovimientoTarea(String.valueOf(log.getId()), log.getUsuario(), log.getFecha(),
                    log.getAccion(), log.getDetalle())).toList()))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Opciones institucionales disponibles para clasificar una tarea. */
    @GetMapping("/proyectos")
    @PreAuthorize("hasAuthority('Tareas:Crear')")
    public List<Map<String, Object>> proyectosDisponibles(Authentication authentication) {
        return modulosDisponibles(authentication).stream().map(modulo -> Map.<String, Object>of(
            "id", modulo.getId(), "nombre", modulo.getNombre(), "descripcion",
            modulo.getDescripcion() == null ? "" : modulo.getDescripcion(), "icono",
            modulo.getIcono() == null ? "" : modulo.getIcono())).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('Tareas:Crear')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        return crearConArchivos(body, List.of());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('Tareas:Crear')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Map<String, Object>> crearMultipart(@RequestPart("datos") Map<String, Object> body,
            @RequestPart(value = "archivos", required = false) List<MultipartFile> files) {
        return crearConArchivos(body, files == null ? List.of() : files);
    }

    private ResponseEntity<Map<String, Object>> crearConArchivos(Map<String, Object> body, List<MultipartFile> files) {
        Tarea t = new Tarea();
        applyTareaBody(t, body);
        if (t.getFechaCreacion() == null) t.setFechaCreacion(tareaVencimientoService.fechaActual());
        validarFechas(t);
        tareaVencimientoService.actualizarEstadoPorFecha(t);
        validarProyectoPermitido(t, SecurityContextHolder.getContext().getAuthentication());
        Usuario creador = tareaAccessService.usuarioActual();
        if (creador == null) return ResponseEntity.status(403).body(Map.of("error", "No se pudo identificar al usuario actual."));
        if (tareaAccessService.esMiembroSinGestion()) {
            t.setAsignadoUsuario(creador);
            t.setAsignado(nombreCompleto(creador));
            t.setParticipantesUsuarios(new LinkedHashSet<>());
        }
        t.setCreadoPor(nombreCompleto(creador));
        incluirCreadorSubdirector(t, creador);
        t.setCodigo(generateCodigo());
        Tarea saved = tareaRepository.save(t);
        saveChecklist(saved.getId(), body);
        saveAdjuntos(saved.getId(), body, files);
        crearNotificaciones(saved);
        return ResponseEntity.ok(toFullTarea(tareaRepository.findById(saved.getId()).orElse(saved)));
    }

    @PostMapping("/importar")
    @PreAuthorize("hasAuthority('Tareas:Importar')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Map<String, Object>> importar(@RequestBody Map<String, Object> body) {
        Tarea t = new Tarea();
        applyTareaBody(t, body);
        if (t.getFechaCreacion() == null) t.setFechaCreacion(tareaVencimientoService.fechaActual());
        validarFechas(t);
        tareaVencimientoService.actualizarEstadoPorFecha(t);
        validarProyectoPermitido(t, SecurityContextHolder.getContext().getAuthentication());
        Usuario creador = tareaAccessService.usuarioActual();
        if (creador == null) return ResponseEntity.status(403).body(Map.of("error", "No se pudo identificar al usuario actual."));
        if (tareaAccessService.esMiembroSinGestion()) {
            t.setAsignadoUsuario(creador);
            t.setAsignado(nombreCompleto(creador));
            t.setParticipantesUsuarios(new LinkedHashSet<>());
        }
        t.setCreadoPor(nombreCompleto(creador));
        incluirCreadorSubdirector(t, creador);
        t.setCodigo(generateCodigo());
        Tarea saved = tareaRepository.save(t);
        saveChecklist(saved.getId(), body);
        saveAdjuntos(saved.getId(), body);
        crearNotificaciones(saved);
        return ResponseEntity.ok(toFullTarea(tareaRepository.findById(saved.getId()).orElse(saved)));
    }

    private void incluirCreadorSubdirector(Tarea tarea, Usuario creador) {
        if (com.sigaa.service.InstitutionalRoles.scopedSubdirector(SecurityContextHolder.getContext().getAuthentication())) {
            tarea.getParticipantesUsuarios().add(creador);
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('Tareas:Editar')")
    @Transactional
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return actualizarConArchivos(id, body, List.of());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('Tareas:Editar')")
    @Transactional
    public ResponseEntity<Map<String, Object>> actualizarMultipart(@PathVariable Long id,
            @RequestPart("datos") Map<String, Object> body,
            @RequestPart(value = "archivos", required = false) List<MultipartFile> files) {
        return actualizarConArchivos(id, body, files == null ? List.of() : files);
    }

    private ResponseEntity<Map<String, Object>> actualizarConArchivos(Long id, Map<String, Object> body,
            List<MultipartFile> files) {
        return tareaRepository.findById(id).map(t -> {
            tareaAccessService.exigirGestion(t);
            ValoresHistorial anteriores = capturarHistorial(t);
            String asignadoAnterior = t.getAsignado();
            String proyectoAnterior = t.getProyecto();
            applyTareaBody(t, body);
            validarFechas(t);
            tareaVencimientoService.actualizarEstadoPorFecha(t);
            if (!Objects.equals(proyectoAnterior, t.getProyecto())) {
                validarProyectoPermitido(t, SecurityContextHolder.getContext().getAuthentication());
            }
            tareaRepository.save(t);
            if (body.containsKey("checklist")) {
                checklistRepository.deleteByTareaId(id);
                saveChecklist(id, body);
            }
            saveAdjuntos(id, body, files);
            registrarCambios(t, anteriores);
            if (body.containsKey("asignado") && !Objects.equals(asignadoAnterior, t.getAsignado())) {
                crearNotificaciones(t);
            }
            return ResponseEntity.ok(toFullTarea(tareaRepository.findById(id).orElse(t)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/asignacion")
    @PreAuthorize("hasAuthority('Tareas:Asignar')")
    @Transactional
    public ResponseEntity<Map<String, Object>> asignar(@PathVariable Long id, @Valid @RequestBody TareaAsignacionRequest body) {
        return tareaRepository.findById(id).map(t -> {
            tareaAccessService.exigirGestion(t);
            ValoresHistorial anteriores = capturarHistorial(t);
            String asignadoAnterior = t.getAsignado();
            aplicarAsignado(t, body);
            aplicarParticipantes(t, body.getParticipanteUsuarioIds());
            if (body.getPrioridad() != null) t.setPrioridad(body.getPrioridad());
            if (body.getFechaLimite() != null) t.setFechaLimite(body.getFechaLimite());
            if (body.getHoraLimite() != null) t.setHoraLimite(body.getHoraLimite());
            validarFechas(t);
            tareaVencimientoService.actualizarEstadoPorFecha(t);
            tareaRepository.save(t);
            registrarCambios(t, anteriores);
            if (!Objects.equals(asignadoAnterior, t.getAsignado())) crearNotificaciones(t);
            return ResponseEntity.ok(toFullTarea(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('Tareas:Completar')")
    @Transactional
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest body, Authentication authentication) {
        boolean administrador = tareaAccessService.esAdministrador();
        String estadoSolicitado = body.getEstado();
        return tareaRepository.findById(id).map(t -> {
            tareaAccessService.exigirLectura(t);
            if (!administrador && !"completada".equalsIgnoreCase(estadoSolicitado)
                    && !tareaAccessService.puedeGestionar(t)) {
                return ResponseEntity.status(403)
                    .body(Map.<String, Object>of("error", "No tienes permisos para realizar esta acción."));
            }
            ValoresHistorial anteriores = capturarHistorial(t);
            t.setEstado(body.getEstado());
            if (body.getProgreso() != null) t.setProgreso(body.getProgreso());
            if ("completada".equalsIgnoreCase(estadoSolicitado)) {
                checklistRepository.findByTareaIdOrderById(id).forEach(item -> item.setCompletado(true));
            }
            tareaRepository.save(t);
            registrarCambios(t, anteriores);
            return ResponseEntity.ok(toFullTarea(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/checklist/{checkId}")
    @PreAuthorize("hasAnyAuthority('Tareas:Completar','Reuniones:Gestionar tareas')")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleChecklist(@PathVariable Long id, @PathVariable Long checkId) {
        Tarea tarea = tareaRepository.findById(id).orElse(null);
        if (tarea == null) return ResponseEntity.notFound().build();
        tareaAccessService.exigirLectura(tarea);
        return checklistRepository.findById(checkId)
            .filter(c -> c.getTareaId().equals(id))
            .map(c -> {
                c.setCompletado(!c.getCompletado());
                checklistRepository.save(c);

                List<TareaChecklist> all = checklistRepository.findByTareaIdOrderById(id);
                long completed = all.stream().filter(TareaChecklist::getCompletado).count();
                int progreso = all.isEmpty() ? 0 : (int) (completed * 100 / all.size());

                return tareaRepository.findById(id).map(t -> {
                    ValoresHistorial anteriores = capturarHistorial(t);
                    t.setProgreso(progreso);
                    if (progreso == 100) t.setEstado("completada");
                    else if (t.getEstado().equals("completada")) t.setEstado("en_progreso");
                    tareaRepository.save(t);
                    registrarCambios(t, anteriores);
                    return ResponseEntity.ok(toFullTarea(t));
                }).orElse(ResponseEntity.notFound().build());
            }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('Tareas:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Tarea tarea = tareaRepository.findById(id).orElse(null);
        if (tarea == null) return ResponseEntity.notFound().build();
        if (!tareaAccessService.esAdministrador()
                && !com.sigaa.service.InstitutionalRoles.scopedSubdirector(SecurityContextHolder.getContext().getAuthentication())) {
            return ResponseEntity.status(403).build();
        }
        tareaAccessService.exigirGestion(tarea);
        // Las claves foráneas de checklist y adjuntos usan ON DELETE CASCADE.
        // Borramos la tarea directamente para evitar un borrado intermedio de JPA.
        tareaAdjuntoService.eliminarArchivosTrasCommit(id);
        tareaRepository.deleteById(id);
        tareaRepository.flush();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/adjuntos/{adjuntoId}/descargar")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Long id, @PathVariable Long adjuntoId) {
        Tarea tarea = tareaRepository.findById(id).orElseThrow(NoSuchElementException::new);
        tareaAccessService.exigirLectura(tarea);
        TareaAdjunto adjunto = adjuntoRepository.findById(adjuntoId)
            .filter(a -> a.getTareaId().equals(id)).orElseThrow(NoSuchElementException::new);
        Resource resource = new FileSystemResource(tareaAdjuntoService.archivo(adjunto));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(adjunto.getTipoContenido() == null
                ? "application/octet-stream" : adjunto.getTipoContenido()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(adjunto.getNombre(), StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }

    /* ---- notificaciones ---- */
    public record MovimientoTarea(String id, String usuario, LocalDateTime fecha, String accion, String detalle) {}

    private record ValoresHistorial(Long responsableId, String responsable, String estado, Integer progreso) {}

    private ValoresHistorial capturarHistorial(Tarea tarea) {
        return new ValoresHistorial(tarea.getAsignadoUsuario() == null ? null : tarea.getAsignadoUsuario().getId(),
            tarea.getAsignado() == null ? "" : tarea.getAsignado().trim().replaceAll("\\s+", " "),
            tarea.getEstado(), tarea.getProgreso());
    }

    private void registrarCambios(Tarea tarea, ValoresHistorial anteriores) {
        ValoresHistorial actuales = capturarHistorial(tarea);
        boolean responsableCambiado = anteriores.responsableId() != null || actuales.responsableId() != null
            ? !Objects.equals(anteriores.responsableId(), actuales.responsableId())
            : !Objects.equals(anteriores.responsable(), actuales.responsable());
        if (responsableCambiado) {
            registrarMovimiento(tarea.getId(), "CAMBIO_RESPONSABLE", "Responsable anterior: " + responsableHistorial(anteriores)
                + ". Nuevo responsable: " + responsableHistorial(actuales) + ".");
        }
        if (!Objects.equals(anteriores.estado(), actuales.estado())) {
            registrarMovimiento(tarea.getId(), "CAMBIO_ESTADO", "Estado anterior: " + estadoHistorial(anteriores.estado())
                + ". Nuevo estado: " + estadoHistorial(actuales.estado()) + ".");
        }
        if (!Objects.equals(anteriores.progreso(), actuales.progreso())) {
            registrarMovimiento(tarea.getId(), "CAMBIO_AVANCE", "Avance anterior: " + anteriores.progreso()
                + "%. Nuevo avance: " + actuales.progreso() + "%.");
        }
    }

    private String responsableHistorial(ValoresHistorial valores) {
        String nombre = valores.responsable().isBlank() ? "Sin responsable" : valores.responsable();
        return valores.responsableId() == null ? nombre : nombre + " (ID: " + valores.responsableId() + ")";
    }

    private String estadoHistorial(String estado) {
        if (estado == null) return "Sin estado";
        return switch (estado) {
            case "por_hacer" -> "Por hacer";
            case "en_progreso" -> "En progreso";
            case "completada" -> "Completada";
            case "vencida" -> "Vencida";
            default -> estado;
        };
    }

    private void registrarMovimiento(Long tareaId, String accion, String detalle) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("No se pudo identificar al autor del cambio");
        }
        AuditLog log = new AuditLog();
        log.setUsuario(authentication.getName());
        log.setEntidad("tarea");
        log.setEntidadId(tareaId);
        log.setAccion(accion);
        log.setDetalle(detalle);
        // Participa en la transacción que ya modifica la tarea; @PrePersist asigna la fecha.
        auditLogRepository.save(log);
    }

    private void crearNotificaciones(Tarea saved) {
        Set<Long> userIds = new HashSet<>();

        if (saved.getAsignadoUsuario() != null) {
            userIds.add(saved.getAsignadoUsuario().getId());
        }
        saved.getParticipantesUsuarios().stream().map(Usuario::getId).forEach(userIds::add);
        String asignado = saved.getAsignado();
        if (userIds.isEmpty() && asignado != null && !asignado.isBlank()) {
            for (String destinatario : asignado.split(",")) {
                String trimmed = destinatario.trim();
                if (trimmed.isEmpty()) continue;
                List<Usuario> matches = usuarioRepository.findByIdentidadExacta(trimmed);
                if (matches.size() == 1) userIds.add(matches.getFirst().getId());
            }
        }

        if (userIds.isEmpty()) return;

        String titulo = "Nueva tarea asignada: " + saved.getTitulo();
        String creadoPor = SecurityContextHolder.getContext().getAuthentication().getName();
        String mensaje = "Se te ha asignado la tarea \"" + saved.getTitulo()
            + "\" (C\u00f3digo: " + saved.getCodigo() + ")"
            + ", creada por " + creadoPor + ".";

        for (Long uid : userIds) {
            Usuario u = usuarioRepository.findById(uid).orElse(null);
            if (u == null) continue;
            internalNotificationService.send("actividad_asignada", u, titulo, mensaje);
            notificationEmailService.sendIfEnabled(u, titulo, mensaje);
        }
    }

    private String nombreCompleto(Usuario usuario) {
        return String.join(" ",
                usuario.getNombre() == null ? "" : usuario.getNombre(),
                usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(),
                usuario.getApellido() == null ? "" : usuario.getApellido())
            .trim().replaceAll("\\s+", " ");
    }

    private List<Modulo> modulosDisponibles(Authentication authentication) {
        if (authentication == null) return List.of();
        boolean administracionGlobal = tareaAccessService.esAdministrador();
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return moduloRepository.findByActivoTrueOrderByOrdenAsc().stream()
            .filter(modulo -> modulo.getNombre() != null && !"tareas".equalsIgnoreCase(modulo.getNombre()))
            .filter(modulo -> administracionGlobal || authorities.contains((modulo.getNombre() + ":Ver").toLowerCase(Locale.ROOT)))
            .toList();
    }

    private void validarProyectoPermitido(Tarea tarea, Authentication authentication) {
        List<Modulo> disponibles = modulosDisponibles(authentication);
        String solicitado = tarea.getProyecto() == null || tarea.getProyecto().isBlank() ? tarea.getTipo() : tarea.getProyecto();
        String proyecto = solicitado == null || solicitado.isBlank()
                ? disponibles.stream().findFirst().map(Modulo::getNombre)
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("No tienes un módulo autorizado para crear tareas"))
                : solicitado;
        boolean permitido = disponibles.stream()
            .anyMatch(modulo -> proyecto.equalsIgnoreCase(modulo.getNombre()));
        if (!permitido) throw new org.springframework.security.access.AccessDeniedException("No puedes crear tareas para el proyecto seleccionado");
        tarea.setProyecto(proyecto);
        tarea.setTipo(proyecto);
    }

    private void aplicarAsignado(Tarea tarea, Map<String, Object> body) {
        if (body.containsKey("asignadoUsuarioId")) {
            Object value = body.get("asignadoUsuarioId");
            if (value == null || String.valueOf(value).isBlank()) {
                tarea.setAsignadoUsuario(null);
            } else {
                Long usuarioId = value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
                Usuario usuario = usuarioRepository.findById(usuarioId)
                        .orElseThrow(() -> new IllegalArgumentException("El usuario asignado no existe"));
                ParticipantePolicy.exigirPuedeParticipar(usuario);
                tarea.setAsignadoUsuario(usuario);
                tarea.setAsignado(nombreCompleto(usuario));
            }
        } else if (body.containsKey("asignado")) {
            String asignado = String.valueOf(body.getOrDefault("asignado", "")).trim();
            List<Usuario> personas = resolverIdentidades(asignado);
            personas.forEach(ParticipantePolicy::exigirPuedeParticipar);
            if (personas.isEmpty()) {
                tarea.setAsignado(asignado);
                tarea.setAsignadoUsuario(null);
            } else {
                tarea.setAsignadoUsuario(personas.getFirst());
                tarea.setAsignado(nombreCompleto(personas.getFirst()));
                tarea.setParticipantesUsuarios(new LinkedHashSet<>(personas.stream().skip(1).toList()));
            }
        }
    }

    private void aplicarAsignado(Tarea tarea, TareaAsignacionRequest body) {
        if (body.getAsignadoUsuarioId() != null) {
            Usuario usuario = usuarioRepository.findById(body.getAsignadoUsuarioId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario asignado no existe"));
            ParticipantePolicy.exigirPuedeParticipar(usuario);
            tarea.setAsignadoUsuario(usuario);
            tarea.setAsignado(nombreCompleto(usuario));
        } else if (body.getAsignado() != null) {
            String asignado = body.getAsignado().trim();
            List<Usuario> personas = resolverIdentidades(asignado);
            personas.forEach(ParticipantePolicy::exigirPuedeParticipar);
            if (personas.isEmpty()) {
                tarea.setAsignado(asignado);
                tarea.setAsignadoUsuario(null);
            } else {
                tarea.setAsignadoUsuario(personas.getFirst());
                tarea.setAsignado(nombreCompleto(personas.getFirst()));
                tarea.setParticipantesUsuarios(new LinkedHashSet<>(personas.stream().skip(1).toList()));
            }
        }
    }

    private List<Usuario> resolverIdentidades(String nombres) {
        if (nombres == null || nombres.isBlank()) return List.of();
        Map<Long, Usuario> personas = new LinkedHashMap<>();
        for (String nombre : nombres.split(",")) {
            List<Usuario> encontrados = usuarioRepository.findByIdentidadExacta(nombre.trim());
            if (encontrados.size() == 1) personas.put(encontrados.getFirst().getId(), encontrados.getFirst());
        }
        return new ArrayList<>(personas.values());
    }

    private void aplicarParticipantes(Tarea tarea, List<Long> participanteUsuarioIds) {
        if (participanteUsuarioIds == null) return;
        List<Long> ids = participanteUsuarioIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Usuario> participantesEncontrados = usuarioRepository.findAllById(ids);
        if (participantesEncontrados.size() != ids.size()) {
            throw new IllegalArgumentException("Uno o más participantes seleccionados no existen");
        }
        participantesEncontrados.forEach(ParticipantePolicy::exigirPuedeParticipar);
        Set<Usuario> participantes = new LinkedHashSet<>(participantesEncontrados);
        if (tarea.getAsignadoUsuario() != null) {
            participantes.removeIf(usuario -> usuario.getId().equals(tarea.getAsignadoUsuario().getId()));
        }
        tarea.setParticipantesUsuarios(participantes);
    }

    /* ---- helpers ---- */
    private void applyTareaBody(Tarea t, Map<String, Object> body) {
        if (body.containsKey("titulo")) t.setTitulo((String) body.get("titulo"));
        if (body.containsKey("descripcion")) t.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("proyecto")) t.setProyecto((String) body.get("proyecto"));
        if (body.containsKey("tipo")) t.setTipo((String) body.get("tipo"));
        aplicarAsignado(t, body);
        if (body.get("participanteUsuarioIds") instanceof List<?> ids) {
            aplicarParticipantes(t, ids.stream().map(value -> value instanceof Number number
                ? number.longValue() : Long.valueOf(String.valueOf(value))).toList());
        }
        if (body.containsKey("cargo")) t.setCargo((String) body.get("cargo"));
        if (body.containsKey("prioridad")) t.setPrioridad((String) body.get("prioridad"));
        if (body.containsKey("fechaLimite")) {
            String value = body.get("fechaLimite") == null ? "" : String.valueOf(body.get("fechaLimite")).trim();
            t.setFechaLimite(value.isBlank() ? null : LocalDate.parse(value));
        }
        if (body.containsKey("horaLimite") && body.get("horaLimite") != null && !((String) body.get("horaLimite")).isBlank())
            t.setHoraLimite(LocalTime.parse((String) body.get("horaLimite")));
        if (body.containsKey("fechaCreacion")) {
            String value = body.get("fechaCreacion") == null ? "" : String.valueOf(body.get("fechaCreacion")).trim();
            // En una edición, un campo vacío no debe borrar la fecha de inicio.
            // En creación se mantiene null para que el flujo asigne la fecha local actual.
            if (!value.isBlank()) t.setFechaCreacion(LocalDate.parse(value));
            else if (t.getId() == null) t.setFechaCreacion(null);
        }
        if (body.containsKey("estado")) t.setEstado((String) body.get("estado"));
        if (body.containsKey("progreso")) t.setProgreso(((Number) body.get("progreso")).intValue());
        if (body.containsKey("unidades")) t.setUnidades((String) body.get("unidades"));
    }

    private void validarFechas(Tarea tarea) {
        if (tarea.getFechaCreacion() != null && tarea.getFechaLimite() != null
                && tarea.getFechaLimite().isBefore(tarea.getFechaCreacion())) {
            throw new IllegalArgumentException("La fecha de finalización no puede ser anterior a la fecha de inicio.");
        }
    }

    @SuppressWarnings("unchecked")
    private void saveChecklist(Long tareaId, Map<String, Object> body) {
        if (body.containsKey("checklist")) {
            for (Map<String, Object> item : (List<Map<String, Object>>) body.get("checklist")) {
                TareaChecklist tc = new TareaChecklist();
                tc.setTareaId(tareaId);
                tc.setTexto((String) item.getOrDefault("texto", ""));
                tc.setCompletado(item.containsKey("completado") && Boolean.parseBoolean(String.valueOf(item.get("completado"))));
                checklistRepository.save(tc);
            }
        }
    }

    private void saveAdjuntos(Long tareaId, Map<String, Object> body) {
        saveAdjuntos(tareaId, body, List.of());
    }

    @SuppressWarnings("unchecked")
    private void saveAdjuntos(Long tareaId, Map<String, Object> body, List<MultipartFile> files) {
        if (body.containsKey("adjuntos")) {
            tareaAdjuntoService.sincronizar(tareaId, (List<Map<String, Object>>) body.get("adjuntos"), files);
        } else if (!files.isEmpty()) {
            throw new IllegalArgumentException("Hay archivos sin asociar a la tarea.");
        }
    }

    private Map<String, Object> toFullTarea(Tarea t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(t.getId()));
        m.put("titulo", t.getTitulo());
        m.put("descripcion", t.getDescripcion());
        m.put("proyecto", t.getProyecto());
        m.put("actaId", t.getActaId());
        m.put("codigo", t.getCodigo());
        m.put("tipo", t.getTipo());
        m.put("asignado", t.getAsignado());
        m.put("asignadoUsuarioId", t.getAsignadoUsuario() == null ? null : t.getAsignadoUsuario().getId());
        List<Usuario> participantesPermitidos = t.getParticipantesUsuarios().stream()
            .filter(usuario -> !ParticipantePolicy.esAdministrador(usuario))
            .toList();
        m.put("participanteUsuarioIds", participantesPermitidos.stream().map(Usuario::getId).toList());
        m.put("participantes", participantesPermitidos.stream().map(this::nombreCompleto).toList());
        m.put("cargo", t.getCargo());
        m.put("unidades", t.getUnidades() != null ? t.getUnidades() : "");
        m.put("prioridad", t.getPrioridad());
        m.put("fechaLimite", t.getFechaLimite() != null ? t.getFechaLimite().toString() : "");
        m.put("horaLimite", t.getHoraLimite() != null ? t.getHoraLimite().toString() : "");
        m.put("fechaCreacion", t.getFechaCreacion() != null ? t.getFechaCreacion().toString() : "");
        m.put("estado", t.getEstado());
        m.put("creadoPor", t.getCreadoPor());
        m.put("progreso", t.getProgreso());

        List<Map<String, Object>> checklist = checklistRepository.findByTareaIdOrderById(t.getId()).stream().map(c -> {
            Map<String, Object> cm = new HashMap<>();
            cm.put("id", String.valueOf(c.getId()));
            cm.put("texto", c.getTexto());
            cm.put("completado", c.getCompletado());
            return cm;
        }).toList();
        m.put("checklist", checklist);

        List<Map<String, Object>> adjuntos = adjuntoRepository.findByTareaIdOrderById(t.getId()).stream().map(a -> {
            Map<String, Object> am = new HashMap<>();
            am.put("nombre", a.getNombre());
            am.put("tamano", a.getTamano());
            am.put("id", String.valueOf(a.getId()));
            am.put("disponible", tareaAdjuntoService.disponible(a));
            am.put("tipoContenido", a.getTipoContenido());
            return am;
        }).toList();
        m.put("adjuntos", adjuntos);

        return m;
    }

    private String generateCodigo() {
        String prefix = "TAR-" + LocalDate.now().getYear() + "-";
        tareaRepository.bloquearGeneracionCodigo();
        long ultimo = tareaRepository.ultimoNumeroCodigo(prefix);
        if (ultimo == Long.MAX_VALUE) throw new IllegalStateException("Se agotó la numeración de tareas");
        return prefix + String.format(Locale.ROOT, "%03d", ultimo + 1);
    }
}
