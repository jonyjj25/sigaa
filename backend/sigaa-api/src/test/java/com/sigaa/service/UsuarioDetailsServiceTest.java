package com.sigaa.service;

import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import com.sigaa.repository.VwUsuarioRolRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioDetailsServiceTest {

    @Test
    void inactiveRolesAndPermissionsDoNotGrantAuthorities() {
        UsuarioRepository users = mock(UsuarioRepository.class);
        VwUsuarioRolRepository roleView = mock(VwUsuarioRolRepository.class);
        UsuarioUnidadRepository memberships = mock(UsuarioUnidadRepository.class);
        UsuarioDetailsService service = new UsuarioDetailsService(users, roleView, memberships,
            mock(com.sigaa.repository.PermissionRepository.class));

        Permission activePermission = permission(10L, "Documentos", "Ver", true);
        Permission inactivePermission = permission(11L, "Documentos", "Eliminar", false);
        Role activeRole = role(20L, "Operador", true, Set.of(activePermission, inactivePermission));
        Role inactiveRole = role(21L, "Archivado", false,
                Set.of(permission(12L, "Seguridad", "Administrar", true)));

        Usuario user = new Usuario();
        user.setId(30L);
        user.setCorreo("seguridad@institucion.local");
        user.setPassword("hash");
        user.setActivo(true);
        user.setBloqueado(false);
        user.setRoles(Set.of(activeRole, inactiveRole));

        when(users.findByCorreo(user.getCorreo())).thenReturn(Optional.of(user));
        when(roleView.findRoleNamesByUsuarioId(user.getId())).thenReturn(List.of());
        when(memberships.findByUsuarioId(user.getId())).thenReturn(List.of());

        UserDetails details = service.loadUserByUsername(user.getCorreo());
        Set<String> authorities = details.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(authorities.contains("Documentos:Ver"));
        assertFalse(authorities.contains("Documentos:Eliminar"));
        assertFalse(authorities.contains("Seguridad:Administrar"));
    }

    private Permission permission(Long id, String module, String action, boolean active) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setModulo(module);
        permission.setAccion(action);
        permission.setRecurso(module);
        permission.setActivo(active);
        return permission;
    }

    private Role role(Long id, String name, boolean active, Set<Permission> permissions) {
        Role role = new Role();
        role.setId(id);
        role.setNombre(name);
        role.setActivo(active);
        role.setPermisos(permissions);
        return role;
    }
}
