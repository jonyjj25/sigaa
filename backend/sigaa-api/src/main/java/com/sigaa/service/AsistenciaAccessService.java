package com.sigaa.service;

import com.sigaa.model.AsistenciaSesion;
import com.sigaa.repository.ActaRepository;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AsistenciaAccessService {
    private final ActaRepository actas;
    private final UsuarioRepository usuarios;
    private final ActaAccessService actaAccess;

    public AsistenciaAccessService(ActaRepository actas, UsuarioRepository usuarios, ActaAccessService actaAccess) {
        this.actas = actas;
        this.usuarios = usuarios;
        this.actaAccess = actaAccess;
    }

    /** Los controladores exigen además el permiso funcional de cada operación. */
    public boolean puedeAcceder(AsistenciaSesion sesion) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (sesion == null || auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return false;
        if (InstitutionalRoles.operational(auth) || auth.getAuthorities().stream()
                .anyMatch(a -> "Configuracion QR:Administrar".equals(a.getAuthority()))
                && !InstitutionalRoles.scopedSubdirector(auth)) return true;
        var usuario = usuarios.findByCorreoIgnoreCase(auth.getName());
        if (usuario.isEmpty()) return false;
        if (sesion.getCreadoPor() != null && sesion.getCreadoPor().equals(usuario.get().getId())) return true;
        // La única asociación persistida con una sesión QR es Acta.sesion.
        // No se infiere una Reunión por coincidencias de título, fecha o código.
        return actas.findBySesionId(sesion.getId()).stream().anyMatch(actaAccess::puedeVer);
    }
}
