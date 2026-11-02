package com.sigaa.service;

import com.sigaa.model.Tarea;
import com.sigaa.model.Usuario;
import com.sigaa.repository.TareaRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Política única de acceso contextual para tareas. */
@Service
@Transactional(readOnly = true)
public class TareaAccessService {
    private final TareaRepository tareas;
    private final UsuarioRepository usuarios;
    private final UsuarioUnidadRepository usuarioUnidades;

    public TareaAccessService(TareaRepository tareas, UsuarioRepository usuarios,
                              UsuarioUnidadRepository usuarioUnidades) {
        this.tareas = tareas;
        this.usuarios = usuarios;
        this.usuarioUnidades = usuarioUnidades;
    }

    public List<Tarea> tareasVisiblesActual() {
        Usuario usuario = usuarioActual();
        if (usuario == null) return List.of();
        if (esAdministrador()) return tareas.findAllByOrderByFechaCreacionDesc();

        Map<Long, Tarea> visibles = new LinkedHashMap<>();
        tareas.findByResponsableOParticipanteUsuarioId(usuario.getId())
            .forEach(tarea -> visibles.put(tarea.getId(), tarea));
        for (String unidad : unidadesDirigidasPor(usuario)) {
            tareas.findByUnidadesContainingIgnoreCaseOrderByFechaCreacionDesc(unidad)
                .forEach(tarea -> visibles.put(tarea.getId(), tarea));
        }
        return visibles.values().stream()
            .sorted(Comparator.comparing(Tarea::getFechaCreacion,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    public boolean puedeVer(Tarea tarea) {
        if (tarea == null) return false;
        if (esAdministrador()) return true;
        Usuario usuario = usuarioActual();
        if (usuario == null) return false;
        boolean relacionado = (tarea.getAsignadoUsuario() != null
                && usuario.getId().equals(tarea.getAsignadoUsuario().getId()))
            || tarea.getParticipantesUsuarios().stream()
                .anyMatch(participante -> usuario.getId().equals(participante.getId()));
        return relacionado || unidadesDirigidasPor(usuario).stream().anyMatch(unidad ->
            unidadesDe(tarea).contains(normalizar(unidad)));
    }

    public boolean puedeGestionar(Tarea tarea) {
        if (esAdministrador()) return true;
        if (InstitutionalRoles.scopedSubdirector(SecurityContextHolder.getContext().getAuthentication())) {
            return puedeVer(tarea);
        }
        Usuario usuario = usuarioActual();
        return usuario != null && unidadesDirigidasPor(usuario).stream()
            .anyMatch(unidad -> unidadesDe(tarea).contains(normalizar(unidad)));
    }

    public void exigirLectura(Tarea tarea) {
        if (!puedeVer(tarea)) throw new AccessDeniedException("No tienes acceso a esta tarea");
    }

    public void exigirGestion(Tarea tarea) {
        if (!puedeGestionar(tarea)) throw new AccessDeniedException("No tienes permisos para modificar esta tarea");
    }

    public Usuario usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return null;
        return usuarios.findByCorreoIgnoreCase(authentication.getName()).orElse(null);
    }

    public boolean esAdministrador() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return InstitutionalRoles.operational(authentication);
    }

    public boolean esMiembroSinGestion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || esAdministrador()) return false;
        boolean miembro = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_miembro".equalsIgnoreCase(authority.getAuthority()));
        return miembro && unidadesDirigidasPor(usuarioActual()).isEmpty();
    }

    private Set<String> unidadesDirigidasPor(Usuario usuario) {
        if (usuario == null) return Set.of();
        return usuarioUnidades.findByUsuarioId(usuario.getId()).stream()
            .filter(membresia -> membresia.getRol() != null
                && (InstitutionalRoles.scopedSubdirector(SecurityContextHolder.getContext().getAuthentication())
                    ? Boolean.TRUE.equals(membresia.getRol().getActivo())
                    : "jefe de unidad".equalsIgnoreCase(membresia.getRol().getNombre())))
            .map(membresia -> membresia.getUnidad())
            .filter(java.util.Objects::nonNull)
            .map(unidad -> unidad.getNombre())
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> unidadesDe(Tarea tarea) {
        if (tarea.getUnidades() == null || tarea.getUnidades().isBlank()) return Set.of();
        return Arrays.stream(tarea.getUnidades().split(","))
            .map(this::normalizar).filter(valor -> !valor.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
