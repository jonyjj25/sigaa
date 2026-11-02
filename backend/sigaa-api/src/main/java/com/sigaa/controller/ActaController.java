package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.ParticipantePolicy;
import com.sigaa.service.ActaAccessService;
import com.sigaa.service.AsistenciaAccessService;
import com.sigaa.util.UploadStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/actas")
public class ActaController {

    private final ActaRepository actaRepository;
    private final ActaParticipanteRepository participanteRepository;
    private final ActaAcuerdoRepository acuerdoRepository;
    private final ActaAdjuntoRepository adjuntoRepository;
    private final ActaFirmaRepository firmaRepository;
    private final AuditLogRepository auditLogRepository;
    private final AsistenciaSesionRepository sesionRepository;
    private final AsistenciaRegistroRepository registroRepository;
    private final AsistenciaInvitadoRepository invitadoRepository;
    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ActaAccessService actaAccess;
    private final AsistenciaAccessService asistenciaAccess;
    private final com.sigaa.service.InternalNotificationService notifications;

    private final Path uploadDir = Paths.get("uploads/actas");

    public ActaController(ActaRepository actaRepository,
                          ActaParticipanteRepository participanteRepository,
                          ActaAcuerdoRepository acuerdoRepository,
                          ActaAdjuntoRepository adjuntoRepository,
                          ActaFirmaRepository firmaRepository,
                          AuditLogRepository auditLogRepository,
                          AsistenciaSesionRepository sesionRepository,
                          AsistenciaRegistroRepository registroRepository,
                          AsistenciaInvitadoRepository invitadoRepository,
                          TareaRepository tareaRepository, UsuarioRepository usuarioRepository,
                          ActaAccessService actaAccess, AsistenciaAccessService asistenciaAccess,
                          com.sigaa.service.InternalNotificationService notifications) {
        this.actaRepository = actaRepository;
        this.notifications = notifications;
        this.participanteRepository = participanteRepository;
        this.acuerdoRepository = acuerdoRepository;
        this.adjuntoRepository = adjuntoRepository;
        this.firmaRepository = firmaRepository;
        this.auditLogRepository = auditLogRepository;
        this.sesionRepository = sesionRepository;
        this.registroRepository = registroRepository;
        this.invitadoRepository = invitadoRepository;
        this.tareaRepository = tareaRepository;
        this.usuarioRepository = usuarioRepository;
        this.actaAccess = actaAccess;
        this.asistenciaAccess = asistenciaAccess;
        try { Files.createDirectories(uploadDir); } catch (IOException e) {}
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Actas:Ver')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return actasVisiblesActual().stream().map(this::toFullActa).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Actas:Ver')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable Long id) {
        return actaRepository.findById(id).filter(this::puedeVerActa)
            .map(a -> ResponseEntity.ok(toFullActa(a)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyAuthority('Reuniones:Generar acta','Actas:Crear')")
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        Acta a = new Acta();
        applyActaBody(a, body);
        a.setCodigo(generateCodigo());
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        linkOrCreateSesion(a, body);
        Acta saved = actaRepository.save(a);
        saveRelated(saved.getId(), body);
        crearTareasDeActaTerminada(saved);
        registrarAudit("CREAR", saved.getId(), "Acta " + saved.getCodigo() + " creada");
        return ResponseEntity.ok(toFullActa(actaRepository.findById(saved.getId()).orElse(saved)));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('Actas:Editar')")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return actaRepository.findById(id).filter(this::puedeVerActa).map(a -> {
            List<String> cambios = detectarCambios(a, body);
            applyActaBody(a, body);
            linkOrCreateSesion(a, body);
            a.setUpdatedAt(LocalDateTime.now());
            actaRepository.saveAndFlush(a);
            saveRelated(id, body);
            crearTareasDeActaTerminada(a);
            String detalle = cambios.isEmpty() ? "Acta actualizada" : "Cambios: " + String.join(", ", cambios);
            registrarAudit("ACTUALIZAR", id, detalle);
            return ResponseEntity.ok(toFullActa(actaRepository.findById(id).orElse(a)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('Actas:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Optional<Acta> opt = actaRepository.findById(id).filter(this::puedeVerActa);
        if (opt.isPresent()) {
            Acta a = opt.get();
            participanteRepository.deleteByActaId(id);
            acuerdoRepository.deleteByActaId(id);
            firmaRepository.deleteByActaId(id);
            List<ActaAdjunto> adjuntos = adjuntoRepository.findByActaIdOrderById(id);
            for (ActaAdjunto adj : adjuntos) {
                try { Files.deleteIfExists(UploadStorage.resolveInside(uploadDir, adj.getRuta())); } catch (IOException | IllegalArgumentException ignored) {}
            }
            adjuntoRepository.deleteByActaId(id);
            actaRepository.deleteById(id);
            registrarAudit("ELIMINAR", id, "Acta " + a.getCodigo() + " eliminada");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/{id}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('Actas:Crear','Actas:Editar','Reuniones:Generar acta')")
    public ResponseEntity<Map<String, Object>> subirAdjunto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Acta acta = actaRepository.findById(id).orElse(null);
        if (acta == null) return ResponseEntity.notFound().build();
        if (!puedeVerActa(acta)) return ResponseEntity.status(403).build();
        try {
            String originalName = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
            Path targetPath = UploadStorage.store(uploadDir, file);
            String storedName = targetPath.getFileName().toString();

            ActaAdjunto adj = new ActaAdjunto();
            adj.setActaId(id);
            adj.setNombreOriginal(originalName);
            adj.setNombreArchivo(storedName);
            adj.setTipoMime(file.getContentType() != null ? file.getContentType() : "");
            adj.setTamano(file.getSize());
            adj.setRuta(targetPath.toString());
            ActaAdjunto saved = adjuntoRepository.save(adj);

            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(saved.getId()));
            m.put("actaId", String.valueOf(saved.getActaId()));
            m.put("nombreOriginal", saved.getNombreOriginal());
            m.put("nombreArchivo", saved.getNombreArchivo());
            m.put("tipoMime", saved.getTipoMime());
            m.put("tamano", saved.getTamano());
            m.put("ruta", "/api/actas/" + id + "/adjuntos/" + saved.getId() + "/descargar");
            return ResponseEntity.ok(m);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/adjuntos/{adjId}/descargar")
    @PreAuthorize("hasAuthority('Actas:Descargar')")
    public ResponseEntity<org.springframework.core.io.Resource> descargarAdjunto(@PathVariable Long id, @PathVariable Long adjId) {
        Acta acta = actaRepository.findById(id).orElse(null);
        if (acta == null || !puedeVerActa(acta)) return ResponseEntity.status(403).build();
        var opt = adjuntoRepository.findById(adjId);
        if (opt.isEmpty() || !opt.get().getActaId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        var adj = opt.get();
        try {
            Path filePath = UploadStorage.resolveInside(uploadDir, adj.getRuta());
            org.springframework.core.io.UrlResource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(adj.getTipoMime().isEmpty() ? "application/octet-stream" : adj.getTipoMime()))
                    .header("Content-Disposition", "attachment; filename=\"" + adj.getNombreOriginal() + "\"")
                    .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* ---- sesion linking ---- */

    private void linkOrCreateSesion(Acta acta, Map<String, Object> body) {
        if (body.containsKey("sesionId") && body.get("sesionId") != null) {
            String sesionIdStr = String.valueOf(body.get("sesionId"));
            if (!sesionIdStr.isEmpty() && !sesionIdStr.equals("null")) {
                Long sesionId = Long.parseLong(sesionIdStr);
                var opt = sesionRepository.findById(sesionId);
                opt.ifPresent(s -> {
                    if (!asistenciaAccess.puedeAcceder(s)) {
                        throw new org.springframework.security.access.AccessDeniedException("No tiene acceso a esta sesión");
                    }
                    acta.setSesion(s);
                });
            }
        } else if (body.containsKey("crearSesion") && Boolean.TRUE.equals(body.get("crearSesion"))) {
            String nombre = acta.getTitulo();
            String tipo = acta.getTipo() != null ? acta.getTipo().toLowerCase() : "reunion";
            AsistenciaSesion sesion = new AsistenciaSesion();
            sesion.setCodigo(generateSesionCodigo());
            sesion.setNombre(nombre);
            sesion.setFecha(acta.getFecha());
            sesion.setTipo(tipo);
            sesion.setActivo(true);
            sesion.setDescripcion("Sesión generada desde acta " + acta.getCodigo());
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) usuarioRepository.findByCorreoIgnoreCase(auth.getName())
                .ifPresent(usuario -> sesion.setCreadoPor(usuario.getId()));
            AsistenciaSesion savedSesion = sesionRepository.save(sesion);
            acta.setSesion(savedSesion);
        }
    }

    private String generateSesionCodigo() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("SESION-");
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        String code = sb.toString();
        if (sesionRepository.findByCodigo(code).isPresent()) return generateSesionCodigo();
        return code;
    }

    /* ---- helpers ---- */

    private List<Acta> actasVisiblesActual() {
        return actaAccess.visiblesActual();
    }

    private boolean puedeVerActa(Acta acta) {
        return actaAccess.puedeVer(acta);
    }

    private void applyActaBody(Acta a, Map<String, Object> body) {
        if (body.containsKey("fecha")) a.setFecha(LocalDate.parse((String) body.get("fecha")));
        if (body.containsKey("hora")) a.setHora(LocalTime.parse((String) body.get("hora")));
        if (body.containsKey("lugar")) a.setLugar((String) body.get("lugar"));
        if (body.containsKey("titulo")) a.setTitulo((String) body.get("titulo"));
        if (body.containsKey("tipo")) a.setTipo((String) body.get("tipo"));
        if (body.containsKey("estado")) a.setEstado((String) body.get("estado"));
        if (body.containsKey("responsable")) a.setResponsable((String) body.get("responsable"));
        if (body.containsKey("objetivos")) a.setObjetivos((String) body.get("objetivos"));
        if (body.containsKey("institucionParticipantes")) {
            a.setInstitucionParticipantes(Objects.toString(body.get("institucionParticipantes"), "").trim());
        }
        if (body.containsKey("temas")) a.setTemas(toJsonArray(body.get("temas")));
        if (body.containsKey("acuerdos")) a.setAcuerdos(toJsonArray(body.get("acuerdos")));
    }

    @SuppressWarnings("unchecked")
    private void saveRelated(Long actaId, Map<String, Object> body) {
        if (body.containsKey("participantes")) {
            IdentidadesAdministrativas administradores = identidadesAdministrativas();
            participanteRepository.deleteByActaId(actaId);
            for (Map<String, Object> p : (List<Map<String, Object>>) body.get("participantes")) {
                if (esIdentidadAdministrativa(p, administradores)) {
                    throw new IllegalArgumentException("Los usuarios Administrador y Superadmin no pueden ser participantes");
                }
                ActaParticipante ap = new ActaParticipante();
                ap.setActaId(actaId);
                ap.setNombre((String) p.getOrDefault("nombre", ""));
                ap.setApellido((String) p.getOrDefault("apellido", ""));
                ap.setArea((String) p.getOrDefault("area", ""));
                ap.setAsistio(p.containsKey("asistio") ? Boolean.parseBoolean(String.valueOf(p.get("asistio"))) : false);
                ap.setCorreo((String) p.getOrDefault("correo", ""));
                ap.setInstitucion((String) p.getOrDefault("institucion", ""));
                ap.setCargo((String) p.getOrDefault("cargo", ""));
                participanteRepository.save(ap);
            }
        }
        if (body.containsKey("acuerdosRel")) {
            acuerdoRepository.deleteByActaId(actaId);
            for (Map<String, Object> p : (List<Map<String, Object>>) body.get("acuerdosRel")) {
                ActaAcuerdo aa = new ActaAcuerdo();
                aa.setActaId(actaId);
                aa.setDescripcion((String) p.getOrDefault("descripcion", ""));
                aa.setResponsable((String) p.getOrDefault("responsable", ""));
                String fechaLimite = Objects.toString(p.get("fechaLimite"), "");
                if (!fechaLimite.isBlank()) aa.setFechaLimite(LocalDate.parse(fechaLimite));
                aa.setEstado((String) p.getOrDefault("estado", "Pendiente"));
                acuerdoRepository.save(aa);
            }
        }
        if (body.containsKey("firmas")) {
            firmaRepository.deleteByActaId(actaId);
            for (Map<String, Object> p : (List<Map<String, Object>>) body.get("firmas")) {
                ActaFirma af = new ActaFirma();
                af.setActaId(actaId);
                af.setNombre((String) p.getOrDefault("nombre", ""));
                af.setRol((String) p.getOrDefault("rol", ""));
                firmaRepository.save(af);
            }
        }
    }

    private void crearTareasDeActaTerminada(Acta acta) {
        if (!"Terminada".equalsIgnoreCase(acta.getEstado())) return;
        actaRepository.bloquearGeneracionCodigo();
        String proyecto = "Acta " + acta.getCodigo();
        List<Tarea> existentes = new ArrayList<>(tareaRepository.findByActaId(acta.getId()));
        existentes.addAll(tareaRepository.findByProyecto(proyecto).stream()
                .filter(t -> t.getActaId() == null && t.getCodigo().startsWith("TAR-ACTA-")).toList());

        for (ActaAcuerdo acuerdo : acuerdoRepository.findByActaIdOrderById(acta.getId())) {
            String responsable = acuerdo.getResponsable() == null ? "" : acuerdo.getResponsable().trim();
            String titulo = acuerdo.getDescripcion() == null ? "" : acuerdo.getDescripcion()
                .replaceFirst("^\\[Compromiso\\]\\s*", "").trim();
            if (responsable.isBlank() || titulo.isBlank()) continue;
            List<Usuario> identificados = usuarioRepository.findByIdentidadExacta(responsable);
            Usuario usuario = identificados.size() == 1 && ParticipantePolicy.puedeParticipar(identificados.getFirst())
                    ? identificados.getFirst() : null;
            String clave;
            try {
                String origen = titulo + "\n" + (usuario == null ? responsable.toLowerCase(Locale.ROOT) : usuario.getId());
                clave = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(origen.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
            if (existentes.stream().anyMatch(t -> clave.equals(t.getAcuerdoClave()))) continue;
            String tituloGuardado = titulo.length() > 300 ? titulo.substring(0, 300) : titulo;
            Tarea anterior = existentes.stream().filter(t -> t.getActaId() == null
                    && tituloGuardado.equals(t.getTitulo()) && responsable.equalsIgnoreCase(t.getAsignado())).findFirst().orElse(null);
            if (anterior != null) {
                anterior.setActaId(acta.getId()); anterior.setAcuerdoClave(clave);
                if (anterior.getAsignadoUsuario() == null) anterior.setAsignadoUsuario(usuario);
                tareaRepository.save(anterior);
                continue;
            }

            Tarea tarea = new Tarea();
            tarea.setCodigo("TAR-ACTA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            tarea.setTitulo(tituloGuardado);
            tarea.setActaId(acta.getId()); tarea.setAcuerdoClave(clave);
            tarea.setDescripcion("Tarea generada desde el acta " + acta.getCodigo() + ": " + acta.getTitulo());
            tarea.setProyecto(proyecto);
            tarea.setTipo("Acta");
            tarea.setAsignado(responsable);
            tarea.setAsignadoUsuario(usuario);
            if (usuario != null) tarea.setUnidades(usuario.getMembresias().stream()
                    .map(UsuarioUnidad::getUnidad).filter(Objects::nonNull)
                    .filter(u -> Boolean.TRUE.equals(u.getActivo())).map(Unidad::getNombre).distinct().collect(java.util.stream.Collectors.joining(", ")));
            tarea.setPrioridad("media");
            tarea.setFechaLimite(acuerdo.getFechaLimite());
            tarea.setFechaCreacion(LocalDate.now());
            tarea.setEstado("por_hacer");
            tarea.setCreadoPor(SecurityContextHolder.getContext().getAuthentication().getName());
            tareaRepository.save(tarea);
            existentes.add(tarea);
            if (usuario != null) avisarTareaTrasCommit(usuario, tarea);
        }
    }

    private void avisarTareaTrasCommit(Usuario usuario, Tarea tarea) {
        String titulo = "Nueva tarea: " + tarea.getTitulo().substring(0, Math.min(140, tarea.getTitulo().length()));
        String mensaje = "Se te asignó una tarea desde " + tarea.getProyecto() + ". Consulta el módulo Tareas.";
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { notifications.sendAfterCommit("actividad_asignada", usuario, titulo, mensaje); }
                    catch (RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(ActaController.class).warn("No se pudo entregar el aviso de una tarea generada desde Actas");
                    }
                }
            });
        } else notifications.send("actividad_asignada", usuario, titulo, mensaje);
    }

    private Map<String, Object> toFullActa(Acta a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("codigo", a.getCodigo());
        m.put("fecha", a.getFecha().toString());
        m.put("hora", a.getHora().toString());
        m.put("lugar", a.getLugar());
        m.put("titulo", a.getTitulo());
        m.put("tipo", a.getTipo());
        m.put("estado", a.getEstado());
        m.put("responsable", a.getResponsable());
        m.put("objetivos", a.getObjetivos());
        m.put("institucionParticipantes", a.getInstitucionParticipantes());
        m.put("temas", parseJsonArray(a.getTemas()));
        m.put("acuerdos", parseJsonArray(a.getAcuerdos()));

        if (a.getSesion() != null) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("id", String.valueOf(a.getSesion().getId()));
            sm.put("codigo", a.getSesion().getCodigo());
            sm.put("nombre", a.getSesion().getNombre());
            sm.put("fecha", a.getSesion().getFecha().toString());
            sm.put("tipo", a.getSesion().getTipo());
            sm.put("activo", a.getSesion().getActivo());
            m.put("sesion", sm);
        } else {
            m.put("sesion", null);
        }

        IdentidadesAdministrativas administradores = identidadesAdministrativas();
        List<Map<String, Object>> participantes = participanteRepository.findByActaIdOrderById(a.getId()).stream()
            .filter(p -> !esIdentidadAdministrativa(Map.of(
                "nombre", Objects.toString(p.getNombre(), ""),
                "apellido", Objects.toString(p.getApellido(), ""),
                "correo", Objects.toString(p.getCorreo(), "")
            ), administradores))
            .map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", String.valueOf(p.getId()));
            pm.put("nombre", p.getNombre());
            pm.put("apellido", p.getApellido());
            pm.put("area", p.getArea());
            pm.put("asistio", p.getAsistio());
            pm.put("correo", p.getCorreo() != null ? p.getCorreo() : "");
            pm.put("institucion", p.getInstitucion() != null ? p.getInstitucion() : "");
            pm.put("cargo", p.getCargo() != null ? p.getCargo() : "");
            pm.put("tipo", "Manual");
            return pm;
        }).toList();

        List<Map<String, Object>> participantesFinales = new ArrayList<>(participantes);

        if (a.getSesion() != null) {
            Set<String> existentes = new HashSet<>();
            for (Map<String, Object> p : participantes) {
                String key = ((String) p.getOrDefault("nombre", "")).trim().toLowerCase()
                    + "|" + ((String) p.getOrDefault("apellido", "")).trim().toLowerCase();
                existentes.add(key);
            }

            Long sesionId = a.getSesion().getId();
            registroRepository.findBySesionIdOrderByHoraEntradaAsc(sesionId).stream()
                .filter(r -> !ParticipantePolicy.esAdministrador(r.getUsuario()))
                .forEach(r -> {
                String nombre = r.getUsuario().getNombre();
                String apellido = r.getUsuario().getApellido() != null ? r.getUsuario().getApellido() : "";
                String key = nombre.trim().toLowerCase() + "|" + apellido.trim().toLowerCase();
                if (!existentes.contains(key)) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", "qr-r-" + r.getId());
                    pm.put("nombre", nombre);
                    pm.put("apellido", apellido);
                    pm.put("correo", r.getUsuario().getCorreo() != null ? r.getUsuario().getCorreo() : "");
                    pm.put("institucion", "");
                    pm.put("cargo", "");
                    pm.put("area", "Sistema QR");
                    pm.put("asistio", true);
                    pm.put("tipo", "Registrado");
                    participantesFinales.add(pm);
                    existentes.add(key);
                } else {
                    for (Map<String, Object> p : participantesFinales) {
                        String pk = ((String) p.getOrDefault("nombre", "")).trim().toLowerCase()
                            + "|" + ((String) p.getOrDefault("apellido", "")).trim().toLowerCase();
                        if (pk.equals(key) && "Manual".equals(p.getOrDefault("tipo", ""))) {
                            p.put("asistio", true);
                            break;
                        }
                    }
                }
            });

            invitadoRepository.findBySesionIdOrderByHoraRegistroDesc(sesionId).stream()
                .filter(inv -> !administradores.correos().contains(normalizar(inv.getCorreo())))
                .forEach(inv -> {
                String key = inv.getNombre().trim().toLowerCase() + "|";
                if (!existentes.contains(key)) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", "qr-i-" + inv.getId());
                    pm.put("nombre", inv.getNombre());
                    pm.put("apellido", "");
                    pm.put("correo", inv.getCorreo() != null ? inv.getCorreo() : "");
                    pm.put("institucion", inv.getInstitucion() != null ? inv.getInstitucion() : "");
                    pm.put("cargo", inv.getCargo() != null ? inv.getCargo() : "");
                    pm.put("area", inv.getInstitucion() != null && !inv.getInstitucion().isEmpty() ? inv.getInstitucion() : "Invitado");
                    pm.put("asistio", true);
                    pm.put("tipo", "Invitado");
                    participantesFinales.add(pm);
                    existentes.add(key);
                }
            });
        }

        m.put("participantes", participantesFinales);

        List<Map<String, Object>> acuerdosRel = acuerdoRepository.findByActaIdOrderById(a.getId()).stream().map(aa -> {
            Map<String, Object> am = new HashMap<>();
            am.put("id", String.valueOf(aa.getId()));
            am.put("descripcion", aa.getDescripcion());
            am.put("responsable", aa.getResponsable());
            am.put("fechaLimite", aa.getFechaLimite() == null ? "" : aa.getFechaLimite().toString());
            am.put("estado", aa.getEstado());
            return am;
        }).toList();
        m.put("acuerdosRel", acuerdosRel);

        List<Map<String, Object>> adjuntos = adjuntoRepository.findByActaIdOrderById(a.getId()).stream().map(adj -> {
            Map<String, Object> am = new HashMap<>();
            am.put("id", String.valueOf(adj.getId()));
            am.put("nombreOriginal", adj.getNombreOriginal());
            am.put("nombreArchivo", adj.getNombreArchivo());
            am.put("tipoMime", adj.getTipoMime());
            am.put("tamano", adj.getTamano());
            return am;
        }).toList();
        m.put("adjuntos", adjuntos);

        List<Map<String, Object>> firmas = firmaRepository.findByActaIdOrderById(a.getId()).stream().map(f -> {
            Map<String, Object> fm = new HashMap<>();
            fm.put("id", String.valueOf(f.getId()));
            fm.put("nombre", f.getNombre());
            fm.put("rol", f.getRol());
            fm.put("fecha", f.getFecha().toString());
            return fm;
        }).toList();
        m.put("firmas", firmas);

        List<Map<String, Object>> historial = auditLogRepository.findByEntidadAndEntidadIdOrderByFechaDesc("acta", a.getId()).stream().map(h -> {
            Map<String, Object> hm = new HashMap<>();
            hm.put("id", String.valueOf(h.getId()));
            hm.put("usuario", h.getUsuario());
            hm.put("accion", h.getAccion());
            hm.put("detalle", h.getDetalle());
            hm.put("fecha", h.getFecha().toString());
            return hm;
        }).toList();
        m.put("historial", historial);

        return m;
    }

    private IdentidadesAdministrativas identidadesAdministrativas() {
        Set<String> correos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        usuarioRepository.findParticipationAdministrators().stream()
            .filter(ParticipantePolicy::esAdministrador)
            .forEach(usuario -> {
                correos.add(normalizar(usuario.getCorreo()));
                nombres.add(normalizar(String.join(" ",
                    Objects.toString(usuario.getNombre(), ""),
                    Objects.toString(usuario.getSegundoNombre(), ""),
                    Objects.toString(usuario.getApellido(), ""))));
                nombres.add(normalizar(String.join(" ",
                    Objects.toString(usuario.getNombre(), ""),
                    Objects.toString(usuario.getApellido(), ""))));
            });
        return new IdentidadesAdministrativas(correos, nombres);
    }

    private boolean esIdentidadAdministrativa(Map<String, Object> participante,
                                               IdentidadesAdministrativas administradores) {
        String correo = normalizar(Objects.toString(participante.get("correo"), ""));
        String nombre = normalizar(String.join(" ",
            Objects.toString(participante.get("nombre"), ""),
            Objects.toString(participante.get("apellido"), "")));
        return (!correo.isEmpty() && administradores.correos().contains(correo))
            || (!nombre.isEmpty() && administradores.nombres().contains(nombre));
    }

    private String normalizar(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record IdentidadesAdministrativas(Set<String> correos, Set<String> nombres) {}

    private void registrarAudit(String accion, Long actaId, String detalle) {
        String usuario = "sistema";
        try {
            usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {}
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad("acta");
        log.setEntidadId(actaId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }

    private List<String> detectarCambios(Acta actual, Map<String, Object> body) {
        List<String> cambios = new ArrayList<>();
        if (body.containsKey("titulo") && !Objects.equals(actual.getTitulo(), body.get("titulo")))
            cambios.add("titulo");
        if (body.containsKey("tipo") && !Objects.equals(actual.getTipo(), body.get("tipo")))
            cambios.add("tipo");
        if (body.containsKey("fecha") && !Objects.equals(actual.getFecha().toString(), body.get("fecha")))
            cambios.add("fecha");
        if (body.containsKey("hora") && !Objects.equals(actual.getHora().toString(), body.get("hora")))
            cambios.add("hora");
        if (body.containsKey("lugar") && !Objects.equals(actual.getLugar(), body.get("lugar")))
            cambios.add("lugar");
        if (body.containsKey("responsable") && !Objects.equals(actual.getResponsable(), body.get("responsable")))
            cambios.add("responsable");
        if (body.containsKey("objetivos") && !Objects.equals(actual.getObjetivos(), body.get("objetivos")))
            cambios.add("objetivos");
        if (body.containsKey("institucionParticipantes")
                && !Objects.equals(actual.getInstitucionParticipantes(), Objects.toString(body.get("institucionParticipantes"), "").trim()))
            cambios.add("institución participantes");
        if (body.containsKey("estado") && !Objects.equals(actual.getEstado(), body.get("estado")))
            cambios.add("estado");
        if (body.containsKey("participantes")) {
            long count = participanteRepository.findByActaIdOrderById(actual.getId()).size();
            long newCount = ((List<?>) body.get("participantes")).size();
            if (count != newCount) cambios.add("participantes (" + count + "→" + newCount + ")");
        }
        if (body.containsKey("acuerdosRel")) {
            long count = acuerdoRepository.findByActaIdOrderById(actual.getId()).size();
            long newCount = ((List<?>) body.get("acuerdosRel")).size();
            if (count != newCount) cambios.add("acuerdos (" + count + "→" + newCount + ")");
        }
        return cambios;
    }

    private String generateCodigo() {
        actaRepository.bloquearGeneracionCodigo();
        long ultimo = actaRepository.ultimoNumeroCodigo();
        if (ultimo == Long.MAX_VALUE) throw new IllegalStateException("Se agotó la numeración de actas");
        return "ACT-" + LocalDate.now().getYear() + "-" + String.format(Locale.ROOT, "%03d", ultimo + 1);
    }

    private String toJsonArray(Object val) {
        if (val instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < ((List<?>) val).size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(((List<?>) val).get(i).toString().replace("\"", "\\\"")).append("\"");
            }
            sb.append("]");
            return sb.toString();
        }
        return "[]";
    }

    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) return result;
        String content = json.substring(1, json.length() - 1);
        int i = 0;
        while (i < content.length()) {
            if (content.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < content.length() && content.charAt(i) != '"') {
                    if (content.charAt(i) == '\\' && i + 1 < content.length()) {
                        sb.append(content.charAt(i + 1));
                        i += 2;
                    } else {
                        sb.append(content.charAt(i));
                        i++;
                    }
                }
                result.add(sb.toString());
            }
            i++;
        }
        return result;
    }
}
