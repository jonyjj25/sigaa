package com.sigaa.service;

import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.PermissionRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import com.sigaa.repository.VwUsuarioRolRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final VwUsuarioRolRepository vwUsuarioRolRepository;
    private final UsuarioUnidadRepository usuarioUnidadRepository;
    private final PermissionRepository permisos;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository,
                                  VwUsuarioRolRepository vwUsuarioRolRepository,
                                  UsuarioUnidadRepository usuarioUnidadRepository, PermissionRepository permisos) {
        this.usuarioRepository = usuarioRepository;
        this.vwUsuarioRolRepository = vwUsuarioRolRepository;
        this.usuarioUnidadRepository = usuarioUnidadRepository;
        this.permisos = permisos;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        List<GrantedAuthority> authorities = new ArrayList<>();

        List<String> roleNames = vwUsuarioRolRepository.findRoleNamesByUsuarioId(usuario.getId())
            .stream()
            .filter(name -> !InstitutionalRoles.retired(name))
            .distinct()
            .collect(Collectors.toList());

        for (String roleName : roleNames) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
            // Se conserva el alias anterior únicamente para los endpoints que
            // todavía lo usan mientras se completa su migración a permisos.
            // Un rol operativo llamado "Administrador" no recibe este alias.
            if ("superadmin".equalsIgnoreCase(roleName.trim())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_superadmin"));
                authorities.add(new SimpleGrantedAuthority("ROLE_administrador"));
            }
        }

        Set<Role> rolesConPermisos = new HashSet<>();
        if (usuario.getRoles() != null) {
            usuario.getRoles().stream()
                    .filter(role -> Boolean.TRUE.equals(role.getActivo()))
                    .filter(role -> !InstitutionalRoles.retired(role.getNombre()))
                    .forEach(rolesConPermisos::add);
        }
        usuarioUnidadRepository.findByUsuarioId(usuario.getId()).stream()
                .map(membership -> membership.getRol())
                .filter(Objects::nonNull)
                .filter(role -> Boolean.TRUE.equals(role.getActivo()))
                .filter(role -> !InstitutionalRoles.retired(role.getNombre()))
                .forEach(rolesConPermisos::add);

        // Sin caché: el Superadmin recibe el catálogo activo vigente, aunque su
        // matriz histórica no contenga un permiso añadido posteriormente.
        if (roleNames.stream().anyMatch(name -> "Superadmin".equals(InstitutionalRoles.canonical(name)))) {
            Role catalogo = new Role();
            catalogo.setPermisos(new HashSet<>(permisos.findByActivoTrue()));
            rolesConPermisos.add(catalogo);
        }
        if (!rolesConPermisos.isEmpty()) {
            Set<Long> processedPermIds = new HashSet<>();
            for (Role role : rolesConPermisos) {
                if (role.getPermisos() != null) {
                    for (Permission perm : role.getPermisos()) {
                        if (Boolean.TRUE.equals(perm.getActivo()) && processedPermIds.add(perm.getId())) {
                            String permKey = perm.getModulo() + ":" + perm.getAccion();
                            authorities.add(new SimpleGrantedAuthority(permKey));
                            String permFull = perm.getModulo() + ":" + perm.getAccion() + ":" + perm.getRecurso();
                            if (!permKey.equals(permFull)) {
                                authorities.add(new SimpleGrantedAuthority(permFull));
                            }
                        }
                    }
                }
            }
        }

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword())
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(usuario.getActivo()))
                .accountLocked(Boolean.TRUE.equals(usuario.getBloqueado()))
                .build();
    }
}
