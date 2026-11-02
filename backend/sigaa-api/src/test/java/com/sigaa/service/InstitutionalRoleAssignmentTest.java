package com.sigaa.service;

import com.sigaa.dto.MembresiaRequest;
import com.sigaa.dto.UsuarioRequest;
import com.sigaa.model.Role;
import com.sigaa.model.Unidad;
import com.sigaa.model.Usuario;
import com.sigaa.model.UsuarioUnidad;
import com.sigaa.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstitutionalRoleAssignmentTest {
    private UsuarioRepository users;
    private RoleRepository roles;
    private UnidadRepository units;
    private UsuarioUnidadRepository memberships;
    private UsuarioService service;
    private Usuario user;

    @BeforeEach
    void setup() {
        users = mock(UsuarioRepository.class);
        roles = mock(RoleRepository.class);
        units = mock(UnidadRepository.class);
        memberships = mock(UsuarioUnidadRepository.class);
        service = new UsuarioService(users, roles, units, memberships, mock(AuditLogRepository.class),
            mock(PasswordHistoryService.class), mock(GrupoMembershipSyncService.class));
        user = new Usuario(); user.setId(1L); user.setCorreo("prueba@example.test");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
    }

    private Role role(Long id, String name) {
        Role role = new Role(name, "Rol de prueba"); role.setId(id); return role;
    }

    private MembresiaRequest membership(Long unitId, Long roleId) {
        MembresiaRequest value = new MembresiaRequest(); value.setUnidadId(unitId); value.setRolId(roleId); return value;
    }

    private void unit(Long id) {
        Unidad unit = new Unidad(); unit.setId(id); unit.setNombre("Unidad de prueba " + id);
        when(units.findById(id)).thenReturn(Optional.of(unit));
        when(units.findForMembershipUpdate(id)).thenReturn(Optional.of(unit));
    }

    @Test
    void acceptsInstitutionalRolesWithoutRemovingExistingUnitMemberships() {
        UsuarioUnidad existing = new UsuarioUnidad();
        user.getMembresias().add(existing);
        for (String name : new String[]{"Director", "Subdirectora", "Secretaria"}) {
            Role role = role(2L, name);
            when(roles.findAllById(Set.of(2L))).thenReturn(List.of(role));
            UsuarioRequest request = new UsuarioRequest(); request.setRolIds(Set.of(2L));
            service.actualizar(1L, request);
            assertEquals(Set.of(role), user.getRoles());
            assertTrue(user.getMembresias().contains(existing));
        }
    }

    @Test
    void rejectsInstitutionalRoleAssignedAsUnitMembership() {
        unit(10L);
        when(roles.findById(2L)).thenReturn(Optional.of(role(2L, "Director")));
        UsuarioRequest request = new UsuarioRequest(); request.setMembresias(List.of(membership(10L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
        verify(users, never()).save(any());
    }

    @Test
    void rejectsRetiredAdministratorEvenIfAHistoricalRowIsStillActive() {
        Role retired = role(2L, "Administrador"); retired.setActivo(true);
        when(roles.findAllById(Set.of(2L))).thenReturn(List.of(retired));
        UsuarioRequest request = new UsuarioRequest(); request.setRolIds(Set.of(2L));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
        verify(users, never()).save(any());
    }

    @Test
    void rejectsDuplicateUnits() {
        unit(10L);
        when(roles.findById(2L)).thenReturn(Optional.of(role(2L, "Miembro")));
        UsuarioRequest request = new UsuarioRequest(); request.setMembresias(List.of(membership(10L, 2L), membership(10L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
        verify(users, never()).save(any());
    }

    @Test
    void rejectsSecondHeadOfTheSameUnit() {
        unit(10L);
        Role head = role(2L, "Jefe de Unidad");
        when(roles.findById(2L)).thenReturn(Optional.of(head));
        Usuario other = new Usuario(); other.setId(99L);
        UsuarioUnidad current = new UsuarioUnidad(); current.setUsuario(other); current.setRol(head);
        when(memberships.findByUnidadId(10L)).thenReturn(List.of(current));
        UsuarioRequest request = new UsuarioRequest(); request.setMembresias(List.of(membership(10L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(1L, request));
        verify(users, never()).save(any());
    }

    @Test
    void allowsTheSameHeadToLeadDifferentUnits() {
        unit(10L); unit(11L);
        Role head = role(2L, "Jefe de Unidad");
        when(roles.findById(2L)).thenReturn(Optional.of(head));
        UsuarioRequest request = new UsuarioRequest();
        request.setMembresias(List.of(membership(10L, 2L), membership(11L, 2L)));
        service.actualizar(1L, request);
        assertEquals(2, user.getMembresias().size());
    }
}
