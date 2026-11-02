package com.sigaa.service;

import com.sigaa.model.Usuario;

/**
 * Regla transversal para las personas que pueden aparecer en selectores y
 * registros operativos de participación.
 */
public final class ParticipantePolicy {

    private ParticipantePolicy() {}

    public static boolean esAdministrador(Usuario usuario) {
        return usuario != null && usuario.getRoles() != null
            && usuario.getRoles().stream().anyMatch(rol -> Boolean.TRUE.equals(rol.getActivo())
                && rol.getNombre() != null && "superadmin".equalsIgnoreCase(rol.getNombre().trim()));
    }

    public static boolean puedeParticipar(Usuario usuario) {
        return usuario != null
            && Boolean.TRUE.equals(usuario.getActivo())
            && !Boolean.TRUE.equals(usuario.getBloqueado())
            && !esAdministrador(usuario);
    }

    public static Usuario exigirPuedeParticipar(Usuario usuario) {
        if (!puedeParticipar(usuario)) {
            throw new IllegalArgumentException("Los usuarios Superadmin no pueden ser participantes");
        }
        return usuario;
    }
}
