package com.sigaa.service;

import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadershipPermissionsTest {
    @Test
    void assignsDirectionPermissionsWithoutOverwritingSubdirectionOrOtherRoles() {
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        Role director = new Role("Director", "Prueba"); director.setId(1L);
        Role deputy = new Role("Subdirectora", "Prueba"); deputy.setId(2L);
        Role secretary = new Role("Secretaria", "Prueba"); secretary.setId(3L);
        Permission permission = new Permission(); permission.setId(10L); permission.setActivo(true);
        Permission deputyPermission = new Permission(); deputyPermission.setId(11L); deputyPermission.setActivo(true);
        deputy.setPermisos(new HashSet<>(Set.of(deputyPermission)));
        when(roles.findById(1L)).thenReturn(Optional.of(director));
        when(roles.save(director)).thenReturn(director);
        when(permissions.findAllById(Set.of(10L))).thenReturn(List.of(permission));
        RoleAdministrationService service = new RoleAdministrationService(roles, permissions, mock(AuditLogRepository.class));
        service.assignPermissions(1L, Set.of(10L), "usuario_prueba", null);
        assertEquals(Set.of(permission), director.getPermisos());
        assertEquals(Set.of(deputyPermission), deputy.getPermisos());
        assertNotSame(director.getPermisos(), deputy.getPermisos());
        assertTrue(secretary.getPermisos().isEmpty());
        verify(roles, never()).save(secretary);
        verify(roles, never()).save(deputy);
    }
}
