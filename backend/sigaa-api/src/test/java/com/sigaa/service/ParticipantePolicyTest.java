package com.sigaa.service;

import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantePolicyTest {

    @Test
    void excludesSuperadminRegardlessOfCase() {
        assertFalse(ParticipantePolicy.puedeParticipar(usuario("SUPERADMIN", true, false)));
        assertThrows(IllegalArgumentException.class,
            () -> ParticipantePolicy.exigirPuedeParticipar(usuario("superadmin", true, false)));
    }

    @Test
    void retiredAdministratorNameDoesNotConferAdministrativePrivileges() {
        Usuario retainedAccount = usuario("Administrador", true, false);
        assertFalse(ParticipantePolicy.esAdministrador(retainedAccount));
        assertTrue(ParticipantePolicy.puedeParticipar(retainedAccount));
    }

    @Test
    void permitsOnlyActiveUnlockedOperationalUsers() {
        assertTrue(ParticipantePolicy.puedeParticipar(usuario("Miembro", true, false)));
        assertFalse(ParticipantePolicy.puedeParticipar(usuario("Miembro", false, false)));
        assertFalse(ParticipantePolicy.puedeParticipar(usuario("Jefe de Unidad", true, true)));
    }

    private Usuario usuario(String roleName, boolean active, boolean blocked) {
        Usuario usuario = new Usuario();
        usuario.setActivo(active);
        usuario.setBloqueado(blocked);
        usuario.setRoles(Set.of(new Role(roleName, "Prueba")));
        return usuario;
    }
}
