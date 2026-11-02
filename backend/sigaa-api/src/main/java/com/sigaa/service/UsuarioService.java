package com.sigaa.service;

import com.sigaa.dto.MembresiaRequest;
import com.sigaa.dto.MembresiaResponse;
import com.sigaa.dto.UsuarioRequest;
import com.sigaa.dto.UsuarioResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import com.sigaa.model.UsuarioUnidad;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.RoleRepository;
import com.sigaa.repository.UnidadRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private static final Set<String> GLOBAL_ROLES = Set.of("Superadmin", "Director", "Subdirector", "Secretaria");
    private static final Set<String> UNIT_ROLES = Set.of("Jefe de Unidad", "Miembro");

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final UnidadRepository unidadRepository;
    private final UsuarioUnidadRepository usuarioUnidadRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordHistoryService passwordHistoryService;
    private final GrupoMembershipSyncService grupoMembershipSyncService;

    public UsuarioService(UsuarioRepository usuarioRepository, RoleRepository roleRepository,
                          UnidadRepository unidadRepository,
                          UsuarioUnidadRepository usuarioUnidadRepository,
                          AuditLogRepository auditLogRepository,
                          PasswordHistoryService passwordHistoryService,
                          GrupoMembershipSyncService grupoMembershipSyncService) {
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.unidadRepository = unidadRepository;
        this.usuarioUnidadRepository = usuarioUnidadRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordHistoryService = passwordHistoryService;
        this.grupoMembershipSyncService = grupoMembershipSyncService;
    }

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UsuarioResponse> listarActivos() {
        return usuarioRepository.findByActivoTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UsuarioResponse> listarSeleccionables() {
        return usuarioRepository.findByActivoTrue().stream()
                .filter(ParticipantePolicy::puedeParticipar)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UsuarioResponse> listarInactivos() {
        return usuarioRepository.findByActivoFalse().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UsuarioResponse> listarBloqueados() {
        return usuarioRepository.findByBloqueadoTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UsuarioResponse obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));
        return toResponse(usuario);
    }

    public List<UsuarioResponse> buscar(String q) {
        return usuarioRepository.buscar(q).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UsuarioResponse> listarPorDepartamento(String departamento) {
        return usuarioRepository.findByDepartamento(departamento).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> estadisticas() {
        long total = usuarioRepository.count();
        long activos = usuarioRepository.countByActivoTrue();
        long inactivos = usuarioRepository.countByActivoFalse();
        long bloqueados = usuarioRepository.countByBloqueadoTrue();
        return Map.of(
                "total", total,
                "activos", activos,
                "inactivos", inactivos,
                "bloqueados", bloqueados
        );
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        GeneratedCredentials credentials = generateCredentials(request.getNombre(), request.getApellido());
        if (request.getDocumentoIdentidad() != null && !request.getDocumentoIdentidad().isBlank()) {
            if (usuarioRepository.existsByDocumentoIdentidad(request.getDocumentoIdentidad())) {
                throw new IllegalArgumentException("El documento de identidad ya está registrado: " + request.getDocumentoIdentidad());
            }
        }
        if (request.getCodigoInterno() != null && !request.getCodigoInterno().isBlank()) {
            if (usuarioRepository.existsByCodigoInterno(request.getCodigoInterno())) {
                throw new IllegalArgumentException("El código interno ya está en uso: " + request.getCodigoInterno());
            }
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }

        Usuario usuario = new Usuario();
        applyRequest(usuario, request);
        // Las credenciales de nuevas cuentas son institucionales y no dependen
        // de valores enviados por el cliente. No altera cuentas existentes.
        usuario.setUsername(credentials.username());
        usuario.setCorreo(credentials.correo());
        passwordHistoryService.initializePassword(usuario, request.getPassword());
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos(0);
        usuario.setRoles(resolveRoles(request.getRolIds()));
        usuario.setMembresias(resolveMembresias(usuario, request.getMembresias()));

        Usuario saved = usuarioRepository.save(usuario);
        grupoMembershipSyncService.sincronizar(saved);
        passwordHistoryService.remember(saved);
        logAudit("CREAR", "Usuario", saved.getId(), "Usuario creado: " + saved.getCorreo());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));
        requireSuperadminForProtectedAccount(usuario);
        boolean estabaActivo = Boolean.TRUE.equals(usuario.getActivo());
        boolean estabaBloqueado = Boolean.TRUE.equals(usuario.getBloqueado());

        if (request.getCorreo() != null && !request.getCorreo().isBlank()) {
            if (usuarioRepository.existsByCorreoAndIdNot(request.getCorreo(), id)) {
                throw new IllegalArgumentException("El correo ya está registrado por otro usuario: " + request.getCorreo());
            }
        }
        if (request.getDocumentoIdentidad() != null && !request.getDocumentoIdentidad().isBlank()) {
            if (usuarioRepository.existsByDocumentoIdentidadAndIdNot(request.getDocumentoIdentidad(), id)) {
                throw new IllegalArgumentException("El documento ya está registrado por otro usuario: " + request.getDocumentoIdentidad());
            }
        }
        if (request.getUsername() != null) {
            String username = normalizeUsername(request.getUsername());
            if (usuarioRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
                throw new IllegalArgumentException("El nombre de usuario ya está en uso: " + username);
            }
        }

        applyRequest(usuario, request);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            passwordHistoryService.changePassword(usuario, request.getPassword());
        }

        if (request.getRolIds() != null) {
            usuario.setRoles(resolveRoles(request.getRolIds()));
        }

        if (request.getMembresias() != null) {
            updateMembresias(usuario, request.getMembresias());
        }

        if (estabaActivo != Boolean.TRUE.equals(usuario.getActivo())
                || estabaBloqueado != Boolean.TRUE.equals(usuario.getBloqueado())) {
            usuario.revokeSessions();
        }

        Usuario saved = usuarioRepository.save(usuario);
        grupoMembershipSyncService.sincronizar(saved);
        logAudit("ACTUALIZAR", "Usuario", saved.getId(), "Usuario actualizado: " + saved.getCorreo());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioResponse toggleBlock(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));

        boolean isAdmin = usuario.getRoles().stream()
                .anyMatch(r -> "superadmin".equalsIgnoreCase(r.getNombre()));
        if (isAdmin) {
            throw new IllegalArgumentException("No se puede bloquear a un Superadmin");
        }

        usuario.setBloqueado(!Boolean.TRUE.equals(usuario.getBloqueado()));
        usuario.revokeSessions();
        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            usuario.setBloqueadoHasta(LocalDateTime.now().plusHours(24));
        } else {
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }

        Usuario saved = usuarioRepository.save(usuario);
        logAudit("TOGGLE_BLOCK", "Usuario", saved.getId(),
                "Usuario " + (saved.getBloqueado() ? "bloqueado" : "desbloqueado") + ": " + saved.getCorreo());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioResponse toggleActivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));

        boolean isAdmin = usuario.getRoles().stream()
                .anyMatch(r -> "superadmin".equalsIgnoreCase(r.getNombre()));
        if (isAdmin && Boolean.TRUE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException("No se puede desactivar a un Superadmin");
        }

        usuario.setActivo(!Boolean.TRUE.equals(usuario.getActivo()));
        usuario.revokeSessions();
        Usuario saved = usuarioRepository.save(usuario);
        logAudit("TOGGLE_ACTIVO", "Usuario", saved.getId(),
                "Usuario " + (saved.getActivo() ? "activado" : "desactivado") + ": " + saved.getCorreo());
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));

        boolean isAdmin = usuario.getRoles().stream()
                .anyMatch(r -> "superadmin".equalsIgnoreCase(r.getNombre()));
        if (isAdmin) {
            throw new IllegalArgumentException("No se puede eliminar a un Superadmin");
        }

        usuario.setActivo(false);
        usuario.revokeSessions();
        usuarioRepository.save(usuario);
        logAudit("ELIMINAR_LOGICO", "Usuario", id, "Usuario desactivado: " + usuario.getCorreo());
    }

    @Transactional
    public void eliminarDefinitivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));

        boolean isAdmin = usuario.getRoles().stream()
                .anyMatch(r -> "superadmin".equalsIgnoreCase(r.getNombre()));
        if (isAdmin) {
            throw new IllegalArgumentException("No se puede eliminar definitivamente a un Superadmin");
        }

        usuarioUnidadRepository.findByUsuarioId(id).forEach(usuarioUnidadRepository::delete);
        usuario.getRoles().clear();
        usuarioRepository.deleteById(id);
        logAudit("ELIMINAR_DEFINITIVO", "Usuario", id, "Usuario eliminado permanentemente: " + usuario.getCorreo());
    }

    @Transactional
    public UsuarioResponse cambiarContrasena(Long id, String nuevaContrasena) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));
        requireSuperadminForProtectedAccount(usuario);

        if (nuevaContrasena == null || nuevaContrasena.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }

        passwordHistoryService.changePassword(usuario, nuevaContrasena);
        Usuario saved = usuarioRepository.save(usuario);
        logAudit("CAMBIAR_CONTRASENA", "Usuario", saved.getId(),
                "Contrasena cambiada para: " + saved.getCorreo());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioResponse forzarCierreSesion(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));
        requireSuperadminForProtectedAccount(usuario);

        usuario.revokeSessions();
        Usuario saved = usuarioRepository.save(usuario);
        logAudit("FORZAR_CIERRE_SESION", "Usuario", saved.getId(),
                "Sesion forzada a cerrar para: " + saved.getCorreo());
        return toResponse(saved);
    }

    private void applyRequest(Usuario usuario, UsuarioRequest request) {
        if (request.getNombre() != null) usuario.setNombre(request.getNombre());
        if (request.getSegundoNombre() != null) usuario.setSegundoNombre(request.getSegundoNombre());
        if (request.getApellido() != null) usuario.setApellido(request.getApellido());
        if (request.getCorreo() != null) usuario.setCorreo(request.getCorreo());
        if (request.getCorreoAlterno() != null) usuario.setCorreoAlterno(request.getCorreoAlterno());
        if (request.getDocumentoIdentidad() != null) usuario.setDocumentoIdentidad(request.getDocumentoIdentidad());
        if (request.getTipoDocumento() != null) usuario.setTipoDocumento(request.getTipoDocumento());
        if (request.getTelefono() != null) usuario.setTelefono(request.getTelefono());
        if (request.getCelular() != null) usuario.setCelular(request.getCelular());
        if (request.getCargo() != null) usuario.setCargo(request.getCargo());
        if (request.getInstitucion() != null) usuario.setInstitucion(request.getInstitucion());
        if (request.getDepartamento() != null) usuario.setDepartamento(request.getDepartamento());
        if (request.getDireccion() != null) usuario.setDireccion(request.getDireccion());
        if (request.getUnidadAdministrativa() != null) usuario.setUnidadAdministrativa(request.getUnidadAdministrativa());
        if (request.getUsername() != null) usuario.setUsername(normalizeUsername(request.getUsername()));
        if (request.getCambioContrasenaRequerido() != null) usuario.setCambioContrasenaRequerido(request.getCambioContrasenaRequerido());
        if (request.getCodigoInterno() != null) usuario.setCodigoInterno(request.getCodigoInterno());
        if (request.getObservaciones() != null) usuario.setObservaciones(request.getObservaciones());
        if (request.getActivo() != null) usuario.setActivo(request.getActivo());
        if (request.getBloqueado() != null) usuario.setBloqueado(request.getBloqueado());
    }

    private Set<Role> resolveRoles(Set<Long> rolIds) {
        if (rolIds == null || rolIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(rolIds));
        if (roles.size() != rolIds.size() || roles.stream().anyMatch(role -> !Boolean.TRUE.equals(role.getActivo()))) {
            throw new IllegalArgumentException("Seleccione roles existentes y activos.");
        }
        if (roles.stream().anyMatch(this::isSuperadminRole) && !currentUserIsSuperadmin()) {
            throw new AccessDeniedException("Solo un Superadmin puede asignar el rol Superadmin.");
        }
        for (Role role : roles) {
            if (!InstitutionalRoles.global(role.getNombre())) {
                throw new IllegalArgumentException(
                        "El rol '" + role.getNombre() + "' no es un rol global. Use solo: " + GLOBAL_ROLES);
            }
        }
        return roles;
    }

    private void requireSuperadminForProtectedAccount(Usuario usuario) {
        if (usuario.getRoles().stream().anyMatch(this::isSuperadminRole) && !currentUserIsSuperadmin()) {
            throw new AccessDeniedException("Solo un Superadmin puede modificar esta cuenta.");
        }
    }

    private boolean isSuperadminRole(Role role) {
        return role != null && "superadmin".equalsIgnoreCase(role.getNombre());
    }

    private boolean currentUserIsSuperadmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_superadmin".equalsIgnoreCase(authority.getAuthority()));
    }

    private Set<UsuarioUnidad> resolveMembresias(Usuario usuario, List<MembresiaRequest> membresias) {
        if (membresias == null) return new HashSet<>();
        validateMembresias(usuario, membresias);
        return membresias.stream().map(m -> {
            Role role = roleRepository.findById(m.getRolId()).orElse(null);
            if (role == null || !Boolean.TRUE.equals(role.getActivo()) || !InstitutionalRoles.unit(role.getNombre())) {
                throw new IllegalArgumentException(
                        "Seleccione un rol de unidad activo. Use solo: " + UNIT_ROLES);
            }
            UsuarioUnidad uu = new UsuarioUnidad();
            uu.setUsuario(usuario);
            if (m.getUnidadId() != null) {
                uu.setUnidad(unidadRepository.findById(m.getUnidadId()).orElse(null));
            }
            uu.setRol(role);
            return uu;
        }).collect(Collectors.toSet());
    }

    private void updateMembresias(Usuario usuario, List<MembresiaRequest> membresias) {
        validateMembresias(usuario, membresias);
        Set<Long> requestedUnidadIds = membresias.stream()
                .map(MembresiaRequest::getUnidadId)
                .collect(Collectors.toSet());

        Map<Long, UsuarioUnidad> existingByUnidad = usuario.getMembresias().stream()
                .collect(Collectors.toMap(m -> m.getUnidad().getId(), Function.identity()));

        usuario.getMembresias().removeIf(m -> !requestedUnidadIds.contains(m.getUnidad().getId()));

        for (MembresiaRequest req : membresias) {
            Role role = roleRepository.findById(req.getRolId()).orElse(null);
            if (role == null || !Boolean.TRUE.equals(role.getActivo()) || !InstitutionalRoles.unit(role.getNombre())) {
                throw new IllegalArgumentException(
                        "Seleccione un rol de unidad activo. Use solo: " + UNIT_ROLES);
            }
            UsuarioUnidad existing = existingByUnidad.get(req.getUnidadId());
            if (existing != null) {
                existing.setRol(role);
            } else {
                UsuarioUnidad uu = new UsuarioUnidad();
                uu.setUsuario(usuario);
                if (req.getUnidadId() != null) {
                    uu.setUnidad(unidadRepository.findById(req.getUnidadId()).orElse(null));
                }
                uu.setRol(role);
                usuario.getMembresias().add(uu);
            }
        }
    }

    private void validateMembresias(Usuario usuario, List<MembresiaRequest> membresias) {
        Set<Long> seen = new HashSet<>();
        for (MembresiaRequest membership : membresias.stream().sorted(java.util.Comparator.comparing(
                MembresiaRequest::getUnidadId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))).toList()) {
            if (membership.getUnidadId() == null || !seen.add(membership.getUnidadId())) {
                throw new IllegalArgumentException("Seleccione una unidad válida sin repetirla.");
            }
            var unit = unidadRepository.findForMembershipUpdate(membership.getUnidadId())
                .orElseThrow(() -> new IllegalArgumentException("La unidad seleccionada no existe."));
            if (!Boolean.TRUE.equals(unit.getActivo())) throw new IllegalArgumentException("La unidad está inactiva.");
            if (membership.getRolId() == null) throw new IllegalArgumentException("Seleccione el rol en la unidad.");
            Role role = roleRepository.findById(membership.getRolId())
                .orElseThrow(() -> new IllegalArgumentException("El rol seleccionado no existe."));
            if (!Boolean.TRUE.equals(role.getActivo()) || !InstitutionalRoles.unit(role.getNombre())) {
                throw new IllegalArgumentException("Solo Jefe de Unidad y Miembro pueden asignarse a una unidad.");
            }
            if ("Jefe de Unidad".equals(InstitutionalRoles.canonical(role.getNombre()))
                    && usuarioUnidadRepository.findByUnidadId(unit.getId()).stream().anyMatch(existing ->
                        existing.getRol() != null && "Jefe de Unidad".equals(InstitutionalRoles.canonical(existing.getRol().getNombre()))
                            && existing.getUsuario() != null && !java.util.Objects.equals(existing.getUsuario().getId(), usuario.getId()))) {
                throw new IllegalArgumentException("La unidad ya tiene un jefe asignado. Cambie esa asignación primero.");
            }
        }
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setId(usuario.getId());
        resp.setNombre(usuario.getNombre());
        resp.setSegundoNombre(usuario.getSegundoNombre());
        resp.setApellido(usuario.getApellido());
        resp.setCorreo(usuario.getCorreo());
        resp.setCorreoAlterno(usuario.getCorreoAlterno());
        resp.setActivo(usuario.getActivo());
        resp.setBloqueado(usuario.getBloqueado());
        resp.setCreadoEn(usuario.getCreadoEn());
        resp.setActualizadoEn(usuario.getActualizadoEn());
        resp.setUltimoAcceso(usuario.getUltimoAcceso());
        resp.setDocumentoIdentidad(usuario.getDocumentoIdentidad());
        resp.setTipoDocumento(usuario.getTipoDocumento());
        resp.setTelefono(usuario.getTelefono());
        resp.setCelular(usuario.getCelular());
        resp.setCargo(usuario.getCargo());
        resp.setInstitucion(usuario.getInstitucion());
        resp.setDepartamento(usuario.getDepartamento());
        resp.setDireccion(usuario.getDireccion());
        resp.setUnidadAdministrativa(usuario.getUnidadAdministrativa());
        resp.setUsername(usuario.getUsername());
        resp.setFechaExpiracion(usuario.getFechaExpiracion());
        resp.setCambioContrasenaRequerido(usuario.getCambioContrasenaRequerido());
        resp.setFotografiaRuta(usuario.getFotografiaRuta());
        resp.setCodigoInterno(usuario.getCodigoInterno());
        resp.setObservaciones(usuario.getObservaciones());

        List<String> roleNames = usuario.getRoles().stream()
                .map(Role::getNombre)
                .collect(Collectors.toList());
        resp.setRoles(roleNames);

        List<MembresiaResponse> membresias = usuarioUnidadRepository.findByUsuarioId(usuario.getId())
                .stream()
                .map(m -> new MembresiaResponse(
                        m.getId(),
                        m.getUnidad() != null ? m.getUnidad().getId() : null,
                        m.getUnidad() != null ? m.getUnidad().getNombre() : null,
                        m.getRol().getId(),
                        m.getRol().getNombre()
                ))
                .collect(Collectors.toList());
        resp.setMembresias(membresias);

        return resp;
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            AuditLog log = new AuditLog();
            log.setUsuario(SecurityContextHolder.getContext().getAuthentication().getName());
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {}
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio");
        }
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("El nombre de usuario no puede exceder 100 caracteres");
        }
        return normalized;
    }

    private GeneratedCredentials generateCredentials(String nombre, String apellido) {
        String nombreNormalizado = normalizeNamePart(nombre);
        String apellidoNormalizado = normalizeNamePart(apellido);
        if (nombreNormalizado.isBlank() || apellidoNormalizado.isBlank()) {
            throw new IllegalArgumentException("El nombre y el apellido son obligatorios para generar las credenciales");
        }

        String base = nombreNormalizado + "." + apellidoNormalizado;
        int suffix = 1;
        while (true) {
            String candidate = appendSuffix(base, suffix);
            String correo = candidate + "@sigaa.com";
            if (!usuarioRepository.existsByUsernameIgnoreCase(candidate)
                    && usuarioRepository.findByCorreoIgnoreCase(correo).isEmpty()) {
                return new GeneratedCredentials(candidate, correo);
            }
            suffix++;
        }
    }

    private String normalizeNamePart(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized;
    }

    private String appendSuffix(String base, int suffix) {
        if (suffix == 1) {
            return base.length() <= 100 ? base : base.substring(0, 100);
        }
        String suffixText = String.valueOf(suffix);
        int baseLength = Math.max(1, 100 - suffixText.length());
        return base.substring(0, Math.min(base.length(), baseLength)) + suffixText;
    }

    private record GeneratedCredentials(String username, String correo) {}
}
