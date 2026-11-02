package com.sigaa.controller;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.ParticipantePolicy;
import com.sigaa.service.AsistenciaAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/asistencia")
public class AsistenciaController {
    private static final Logger log = LoggerFactory.getLogger(AsistenciaController.class);

    private final AsistenciaRegistroRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final AsistenciaSesionRepository sesionRepository;
    private final AsistenciaInvitadoRepository invitadoRepository;
    private final ActaRepository actaRepository;
    private final AsistenciaAccessService asistenciaAccess;

    public AsistenciaController(AsistenciaRegistroRepository repository,
                                 UsuarioRepository usuarioRepository,
                                 AsistenciaSesionRepository sesionRepository,
                                 AsistenciaInvitadoRepository invitadoRepository,
                                 ActaRepository actaRepository, AsistenciaAccessService asistenciaAccess) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
        this.invitadoRepository = invitadoRepository;
        this.actaRepository = actaRepository;
        this.asistenciaAccess = asistenciaAccess;
    }

    @GetMapping("/historial")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerHistorial(Authentication auth) {
        Long usuarioId = extractUsuarioId(auth);
        return repository.findByUsuarioIdAndSesionIsNotNullOrderByFechaDesc(usuarioId).stream()
                .map(this::toMap)
                .toList();
    }

    /* ===== OPERATIONAL ENDPOINTS ===== */

    @GetMapping("/usuarios")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public ResponseEntity<?> buscarUsuarios(@RequestParam(value = "q", required = false) String query) {
        List<Usuario> usuarios = usuarioRepository.findAll().stream()
                .filter(ParticipantePolicy::puedeParticipar)
                .toList();
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase();
            usuarios = usuarios.stream()
                .filter(u -> u.getNombre().toLowerCase().contains(q)
                    || u.getCorreo().toLowerCase().contains(q))
                .toList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Usuario u : usuarios) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nombre", u.getNombre());
            m.put("correo", u.getCorreo());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/escanear")
    public ResponseEntity<Map<String, Object>> escanearQr(@RequestBody Map<String, Object> body, Authentication auth) {
        try {
            String uidStr = body.containsKey("uid") ? body.get("uid").toString() : null;
            String codigo = body.containsKey("codigo") ? body.get("codigo").toString() : null;
            if (uidStr == null || codigo == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan parámetros: uid y código"));
            }
            Long uid;
            try {
                uid = Long.parseLong(uidStr);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "UID inválido"));
            }
            var sesionOpt = sesionRepository.findByCodigo(codigo);
            if (sesionOpt.isEmpty() || !sesionDisponibleParaRegistro(sesionOpt.get())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Sesión no encontrada o inactiva"));
            }
            var usuarioOpt = usuarioRepository.findByCorreoIgnoreCase(auth.getName());
            if (usuarioOpt.isEmpty() || !uid.equals(usuarioOpt.get().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Solo puedes registrar tu propia asistencia"));
            }
            if (ParticipantePolicy.esAdministrador(usuarioOpt.get())) {
                return ResponseEntity.status(403).body(Map.of("error", "Los usuarios Administrador y Superadmin no pueden registrarse como participantes"));
            }
            if (!sesionVigente(sesionOpt.get())) {
                return ResponseEntity.badRequest().body(Map.of("error", "La sesión está expirada"));
            }
            if (repository.existsByUsuarioIdAndSesionId(uid, sesionOpt.get().getId())) {
                return ResponseEntity.status(409).body(Map.of("error", "Ya registraste asistencia en esta sesión"));
            }

            AsistenciaRegistro r = new AsistenciaRegistro();
            r.setUsuario(usuarioOpt.get());
            r.setFecha(LocalDate.now());
            r.setHoraEntrada(LocalTime.now());
            r.setLatEntrada(0);
            r.setLngEntrada(0);
            r.setMetodo("QR");
            r.setEstado("presente");
            r.setSesion(sesionOpt.get());
            repository.save(r);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("message", "Asistencia registrada correctamente para " + usuarioOpt.get().getNombre());
            res.put("usuario", usuarioOpt.get().getNombre());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("Error interno al registrar asistencia QR: {}", LogDiagnostics.describe(e));
            return ResponseEntity.status(500).body(Map.of("error", "Error interno al registrar asistencia"));
        }
    }

    @PostMapping("/invitado")
    public ResponseEntity<Map<String, Object>> registrarInvitado(@RequestBody Map<String, Object> body) {
        try {
            String codigo = body.containsKey("codigoSesion") ? body.get("codigoSesion").toString() : null;
            String nombre = body.containsKey("nombre") ? body.get("nombre").toString() : null;
            String correo = body.containsKey("correo") ? body.get("correo").toString() : null;
            if (codigo == null || nombre == null || correo == null || nombre.trim().isEmpty() || correo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan campos obligatorios: código, nombre, correo"));
            }
            var sesionOpt = sesionRepository.findByCodigo(codigo);
            if (sesionOpt.isEmpty() || !sesionDisponibleParaRegistro(sesionOpt.get()) || !sesionVigente(sesionOpt.get())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Sesión no encontrada o inactiva"));
            }
            if (usuarioRepository.findByCorreoIgnoreCase(correo.trim())
                    .map(ParticipantePolicy::esAdministrador).orElse(false)) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudo registrar la asistencia con los datos proporcionados"));
            }
            if (invitadoRepository.existsBySesionIdAndCorreoIgnoreCase(sesionOpt.get().getId(), correo.trim())) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Asistencia registrada correctamente"));
            }
            AsistenciaInvitado inv = new AsistenciaInvitado();
            inv.setSesionId(sesionOpt.get().getId());
            inv.setNombre(nombre.trim());
            inv.setCorreo(correo.trim());
            inv.setTelefono(body.containsKey("telefono") ? body.get("telefono").toString() : "");
            inv.setInstitucion(body.containsKey("institucion") ? body.get("institucion").toString() : "");
            inv.setCargo(body.containsKey("cargo") ? body.get("cargo").toString() : "");
            inv.setAceptaTerminos(true);
            inv.setHoraRegistro(LocalDateTime.now());
            invitadoRepository.save(inv);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("message", "Asistencia registrada correctamente");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("Error interno al registrar asistencia de invitado: {}", LogDiagnostics.describe(e));
            return ResponseEntity.status(500).body(Map.of("error", "Error interno al registrar invitado"));
        }
    }

    @GetMapping("/sesiones/{codigo}")
    public ResponseEntity<Map<String, Object>> obtenerSesionPorCodigo(@PathVariable String codigo) {
        var opt = sesionRepository.findByCodigo(codigo);
        if (opt.isEmpty() || !sesionDisponibleParaRegistro(opt.get()) || !sesionVigente(opt.get())) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toPublicMapSesion(opt.get()));
    }

    @GetMapping("/sesiones")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Ver') or hasAuthority('Configuracion QR:Administrar')")
    public List<Map<String, Object>> listarSesiones(
            @RequestParam(value = "archivadas", defaultValue = "false") boolean archivadas) {
        archivarSesionesAntiguas();
        List<AsistenciaSesion> sesiones = archivadas
                ? sesionRepository.findByArchivadoTrueOrderByFechaDescCreadoEnDesc()
                : sesionRepository.findByArchivadoFalseOrderByFechaDescCreadoEnDesc();
        return sesiones.stream()
                .filter(asistenciaAccess::puedeAcceder)
                .map(s -> toMapSesion(s, true))
                .toList();
    }

    @PostMapping("/sesiones")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Crear') or hasAuthority('Configuracion QR:Administrar')")
    public ResponseEntity<Map<String, Object>> crearSesion(@RequestBody Map<String, Object> body) {
        AsistenciaSesion s = new AsistenciaSesion();
        s.setCodigo(generateCodigo());
        s.setNombre(body.containsKey("nombre") ? body.get("nombre").toString() : "Sesión");
        s.setDescripcion(body.containsKey("descripcion") ? body.get("descripcion").toString() : "");
        if (body.containsKey("fecha")) {
            if (!(body.get("fecha") instanceof String fecha)) {
                throw new IllegalArgumentException("La fecha debe ser una fecha válida con formato AAAA-MM-DD.");
            }
            try { s.setFecha(LocalDate.parse(fecha)); }
            catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("La fecha debe ser una fecha válida con formato AAAA-MM-DD.", ex);
            }
        } else s.setFecha(LocalDate.now());
        if (body.containsKey("horaInicio")) s.setHoraInicio(LocalTime.parse(body.get("horaInicio").toString()));
        if (body.containsKey("horaFin")) s.setHoraFin(LocalTime.parse(body.get("horaFin").toString()));
        s.setTipo(body.containsKey("tipo") ? body.get("tipo").toString() : "reunion");
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                usuarioRepository.findByCorreo(auth.getName()).ifPresent(u -> s.setCreadoPor(u.getId()));
            }
        } catch (Exception ignored) {}
        s.setActivo(true);
        s.setArchivado(false);
        sesionRepository.save(s);
        return ResponseEntity.ok(toMapSesion(s));
    }

    @DeleteMapping("/sesiones/{id}")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Eliminar') or hasAuthority('Configuracion QR:Administrar')")
    public ResponseEntity<?> eliminarSesion(@PathVariable Long id) {
        var sesion = sesionRepository.findById(id).filter(asistenciaAccess::puedeAcceder);
        if (sesion.isEmpty()) return ResponseEntity.notFound().build();

        long asistencias = repository.countBySesionId(id);
        long actas = actaRepository.countBySesionId(id);
        if (asistencias > 0 || actas > 0) {
            List<String> dependencias = new ArrayList<>();
            if (asistencias > 0) dependencias.add(asistencias + (asistencias == 1 ? " asistencia registrada" : " asistencias registradas"));
            if (actas > 0) dependencias.add(actas + (actas == 1 ? " acta vinculada" : " actas vinculadas"));
            return ResponseEntity.status(409).body(Map.of(
                    "error", "No se puede eliminar la sesión porque conserva historial",
                    "message", "La sesión tiene " + String.join(" y ", dependencias) + ". Ciérrala para desactivar el QR sin borrar el historial.",
                    "asistencias", asistencias,
                    "actas", actas
            ));
        }

        sesionRepository.delete(sesion.get());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/sesiones/{id}/cerrar")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Editar') or hasAuthority('Configuracion QR:Administrar')")
    public ResponseEntity<Map<String, Object>> cerrarSesion(@PathVariable Long id) {
        return sesionRepository.findById(id)
                .filter(asistenciaAccess::puedeAcceder)
                .map(sesion -> {
                    sesion.setActivo(false);
                    return ResponseEntity.ok(toMapSesion(sesionRepository.save(sesion)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/sesiones/{id}/archivar")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Editar') or hasAuthority('Configuracion QR:Administrar')")
    public ResponseEntity<Map<String, Object>> archivarSesion(@PathVariable Long id) {
        return sesionRepository.findById(id)
                .filter(asistenciaAccess::puedeAcceder)
                .map(sesion -> {
                    sesion.setActivo(false);
                    sesion.setArchivado(true);
                    if (sesion.getArchivadoEn() == null) sesion.setArchivadoEn(LocalDateTime.now());
                    return ResponseEntity.ok(toMapSesion(sesionRepository.save(sesion), true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/sesiones/{id}/restaurar")
    @Transactional
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Editar') or hasAuthority('Configuracion QR:Administrar')")
    public ResponseEntity<?> restaurarSesion(@PathVariable Long id) {
        return sesionRepository.findById(id)
                .filter(asistenciaAccess::puedeAcceder)
                .map(sesion -> {
                    if (sesion.getFecha() != null && sesion.getFecha().isBefore(LocalDate.now())) {
                        return ResponseEntity.status(409).body(Map.of(
                                "error", "La sesión corresponde a una fecha anterior",
                                "message", "Las sesiones antiguas deben permanecer en el archivo."
                        ));
                    }
                    sesion.setArchivado(false);
                    sesion.setArchivadoEn(null);
                    sesion.setActivo(false);
                    return ResponseEntity.ok(toMapSesion(sesionRepository.save(sesion), true));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sesiones/{codigo}/invitados")
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Ver') or hasAuthority('Configuracion QR:Administrar') or hasAuthority('Actas:Ver')")
    public ResponseEntity<List<Map<String, Object>>> listarInvitados(@PathVariable String codigo) {
        var sesionOpt = sesionRepository.findByCodigo(codigo).filter(asistenciaAccess::puedeAcceder);
        if (sesionOpt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(invitadoRepository.findBySesionIdOrderByHoraRegistroDesc(sesionOpt.get().getId()).stream()
                .filter(inv -> usuarioRepository.findByCorreoIgnoreCase(inv.getCorreo())
                    .map(usuario -> !ParticipantePolicy.esAdministrador(usuario)).orElse(true))
                .map(this::toMapInvitado)
                .toList());
    }

    @GetMapping("/sesiones/{codigo}/asistentes")
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion QR:Ver') or hasAuthority('Configuracion QR:Administrar') or hasAuthority('Actas:Ver')")
    public ResponseEntity<Map<String, Object>> obtenerAsistentes(@PathVariable String codigo) {
        var sesionOpt = sesionRepository.findByCodigo(codigo).filter(asistenciaAccess::puedeAcceder);
        if (sesionOpt.isEmpty()) return ResponseEntity.notFound().build();
        AsistenciaSesion sesion = sesionOpt.get();

        List<Map<String, Object>> registrados = repository.findBySesionIdOrderByHoraEntradaAsc(sesion.getId()).stream()
                .filter(r -> !ParticipantePolicy.esAdministrador(r.getUsuario()))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("usuarioId", r.getUsuario().getId());
                    m.put("nombre", r.getUsuario().getNombre());
                    m.put("correo", r.getUsuario().getCorreo());
                    m.put("horaEntrada", r.getHoraEntrada().toString());
                    m.put("horaSalida", r.getHoraSalida() != null ? r.getHoraSalida().toString() : null);
                    m.put("metodo", r.getMetodo());
                    m.put("estado", r.getEstado());
                    m.put("tipo", "Registrado");
                    return m;
                }).toList();

        List<Map<String, Object>> invitados = invitadoRepository.findBySesionIdOrderByHoraRegistroDesc(sesion.getId()).stream()
                .filter(inv -> usuarioRepository.findByCorreoIgnoreCase(inv.getCorreo())
                    .map(usuario -> !ParticipantePolicy.esAdministrador(usuario)).orElse(true))
                .map(inv -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", inv.getId());
                    m.put("nombre", inv.getNombre());
                    m.put("correo", inv.getCorreo());
                    m.put("horaRegistro", inv.getHoraRegistro().toString());
                    m.put("tipo", "Invitado");
                    return m;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> resumen = new LinkedHashMap<>(toPublicMapSesion(sesion));
        resumen.put("id", sesion.getId());
        resumen.put("codigo", sesion.getCodigo());
        result.put("sesion", resumen);
        result.put("registrados", registrados);
        result.put("invitados", invitados);
        result.put("total", registrados.size() + invitados.size());
        return ResponseEntity.ok(result);
    }

    /* ===== HELPERS ===== */

    private Long extractUsuarioId(Authentication auth) {
        String correo = auth.getName();
        return usuarioRepository.findByCorreo(correo)
            .map(Usuario::getId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + correo));
    }

    private String generateCodigo() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("SESION-");
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private boolean sesionVigente(AsistenciaSesion sesion) {
        if (sesion.getQrExpiraEn() != null) return !sesion.getQrExpiraEn().isBefore(LocalDateTime.now());
        return sesion.getFecha() == null || sesion.getFecha().equals(LocalDate.now());
    }

    private boolean sesionDisponibleParaRegistro(AsistenciaSesion sesion) {
        return Boolean.TRUE.equals(sesion.getActivo()) && !Boolean.TRUE.equals(sesion.getArchivado());
    }

    private void archivarSesionesAntiguas() {
        List<AsistenciaSesion> antiguas = sesionRepository.findByArchivadoFalseAndFechaBefore(LocalDate.now());
        if (antiguas.isEmpty()) return;

        LocalDateTime archivadoEn = LocalDateTime.now();
        antiguas.forEach(sesion -> {
            sesion.setActivo(false);
            sesion.setArchivado(true);
            sesion.setArchivadoEn(archivadoEn);
        });
        sesionRepository.saveAll(antiguas);
    }

    private Map<String, Object> toPublicMapSesion(AsistenciaSesion sesion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nombre", sesion.getNombre());
        result.put("fecha", sesion.getFecha().toString());
        result.put("horaInicio", sesion.getHoraInicio() != null ? sesion.getHoraInicio().toString() : null);
        return result;
    }

    private Map<String, Object> toMap(AsistenciaRegistro r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("usuarioId", r.getUsuario().getId());
        m.put("usuarioNombre", r.getUsuario().getNombre());
        m.put("fecha", r.getFecha().toString());
        m.put("horaEntrada", r.getHoraEntrada().toString());
        m.put("horaSalida", r.getHoraSalida() != null ? r.getHoraSalida().toString() : null);
        m.put("latEntrada", r.getLatEntrada());
        m.put("lngEntrada", r.getLngEntrada());
        m.put("latSalida", r.getLatSalida());
        m.put("lngSalida", r.getLngSalida());

        Map<String, Object> ubic = new LinkedHashMap<>();
        if (r.getSede() != null) {
            ubic.put("id", r.getSede().getId());
            ubic.put("nombre", r.getSede().getNombre());
            ubic.put("lat", r.getSede().getLatitud());
            ubic.put("lng", r.getSede().getLongitud());
            ubic.put("radio", r.getSede().getRadio());
        }
        m.put("sede", ubic);

        m.put("metodo", r.getMetodo());
        m.put("estado", r.getEstado());
        m.put("creadoEn", r.getCreadoEn() != null ? r.getCreadoEn().toString() : null);
        return m;
    }

    private Map<String, Object> toMapSesion(AsistenciaSesion s) {
        return toMapSesion(s, false);
    }

    private Map<String, Object> toMapSesion(AsistenciaSesion s, boolean includeCounts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("codigo", s.getCodigo());
        m.put("nombre", s.getNombre());
        m.put("descripcion", s.getDescripcion());
        m.put("fecha", s.getFecha().toString());
        m.put("horaInicio", s.getHoraInicio() != null ? s.getHoraInicio().toString() : null);
        m.put("horaFin", s.getHoraFin() != null ? s.getHoraFin().toString() : null);
        m.put("tipo", s.getTipo());
        m.put("activo", s.getActivo());
        m.put("archivado", Boolean.TRUE.equals(s.getArchivado()));
        m.put("archivadoEn", s.getArchivadoEn() != null ? s.getArchivadoEn().toString() : null);
        m.put("creadoEn", s.getCreadoEn().toString());
        if (includeCounts) {
            m.put("totalAsistencias", repository.countBySesionId(s.getId()));
            m.put("totalInvitados", invitadoRepository.countBySesionId(s.getId()));
            m.put("totalActas", actaRepository.countBySesionId(s.getId()));
        }
        return m;
    }

    private Map<String, Object> toMapInvitado(AsistenciaInvitado inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("nombre", inv.getNombre());
        m.put("correo", inv.getCorreo());
        m.put("horaRegistro", inv.getHoraRegistro().toString());
        return m;
    }
}
