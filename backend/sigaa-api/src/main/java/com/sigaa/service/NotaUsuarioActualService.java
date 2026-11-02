package com.sigaa.service;

import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resuelve el usuario de la sesión; nunca acepta un id enviado por el cliente. */
@Service
public class NotaUsuarioActualService {
    private final UsuarioRepository usuarioRepository;

    public NotaUsuarioActualService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario actual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("La sesión no es válida.");
        }
        return usuarioRepository.findByCorreoIgnoreCase(authentication.getName())
            .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()) && !Boolean.TRUE.equals(usuario.getBloqueado()))
            .orElseThrow(() -> new AccessDeniedException("Usuario no válido."));
    }

    public boolean tieneAutoridad(String autoridad) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(item -> autoridad.equals(item.getAuthority()));
    }
}
