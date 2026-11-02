package com.sigaa.service;

import com.sigaa.model.Acta;
import com.sigaa.model.Usuario;
import com.sigaa.repository.ActaRepository;
import com.sigaa.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Política de consulta existente de Actas, compartida con sus sesiones de asistencia. */
@Service
@Transactional(readOnly = true)
public class ActaAccessService {
    private final ActaRepository actas;
    private final UsuarioRepository usuarios;

    public ActaAccessService(ActaRepository actas, UsuarioRepository usuarios) {
        this.actas = actas;
        this.usuarios = usuarios;
    }

    public List<Acta> visiblesActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!autenticado(auth)) return List.of();
        if (!restringido(auth)) return actas.findAllByOrderByFechaDesc();
        Usuario usuario = usuarioActual(auth);
        return usuario == null ? List.of() : visibles(usuario, auth);
    }

    public boolean puedeVer(Acta acta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (acta == null || !autenticado(auth)) return false;
        if (!restringido(auth)) return true;
        Usuario usuario = usuarioActual(auth);
        return usuario != null && visibles(usuario, auth).stream()
            .anyMatch(visible -> visible.getId().equals(acta.getId()));
    }

    private boolean restringido(Authentication auth) {
        if (InstitutionalRoles.operational(auth)) return false;
        if (InstitutionalRoles.scopedSubdirector(auth)) return true;
        // Los permisos funcionales de otros cargos no otorgan alcance global.
        return !InstitutionalRoles.hasRole(auth, "Jefe de Unidad")
            && !(InstitutionalRoles.hasRole(auth, "Secretaria") && !InstitutionalRoles.hasRole(auth, "Miembro"));
    }

    private List<Acta> visibles(Usuario usuario, Authentication auth) {
        return InstitutionalRoles.scopedSubdirector(auth)
            ? actas.findByAmbitoSubdireccion(usuario.getId(), usuario.getCorreo(), nombreCompleto(usuario))
            : actas.findByParticipanteUsuario(usuario.getCorreo(), nombreCompleto(usuario));
    }

    private boolean autenticado(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private Usuario usuarioActual(Authentication auth) {
        return usuarios.findByCorreoIgnoreCase(auth.getName()).orElse(null);
    }

    private String nombreCompleto(Usuario usuario) {
        return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(),
            usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(),
            usuario.getApellido() == null ? "" : usuario.getApellido()).trim().replaceAll("\\s+", " ");
    }
}
