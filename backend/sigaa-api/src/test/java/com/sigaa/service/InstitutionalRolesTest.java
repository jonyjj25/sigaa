package com.sigaa.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class InstitutionalRolesTest {
    @Test
    void recognizesEquivalentTitles() {
        assertEquals(InstitutionalRoles.canonical("Director"), InstitutionalRoles.canonical(" Directora "));
        assertEquals(InstitutionalRoles.canonical("Subdirector"), InstitutionalRoles.canonical("Subdirectora"));
        assertEquals("Secretaria", InstitutionalRoles.canonical("Secretaría"));
    }

    @Test
    void separatesInstitutionalRolesFromUnitMemberships() {
        for (String name : new String[]{"Director", "Subdirectora", "Secretaría", "Superadmin"}) {
            assertTrue(InstitutionalRoles.global(name));
            assertFalse(InstitutionalRoles.unit(name));
        }
        for (String name : new String[]{"Jefe de Unidad", "Jefa de Unidad", "Miembro"}) {
            assertTrue(InstitutionalRoles.unit(name));
            assertFalse(InstitutionalRoles.global(name));
        }
        assertFalse(InstitutionalRoles.global("Personalizado"));
        assertFalse(InstitutionalRoles.global("Administrador"));
        assertTrue(InstitutionalRoles.retired(" administrador "));
    }

    @Test
    void givesOnlySuperadminAndDirectorGlobalOperationalScope() {
        for (String name : new String[]{"Director", "Directora", "Superadmin"}) {
            var authentication = new UsernamePasswordAuthenticationToken("usuario_prueba", "TEST_ONLY_NOT_REAL",
                AuthorityUtils.createAuthorityList("ROLE_" + name));
            assertTrue(InstitutionalRoles.operational(authentication));
        }
    }

    @Test
    void doesNotGiveSecretariatOrUnitRolesGeneralOperationalScope() {
        for (String name : new String[]{"Secretaria", "Jefe de Unidad", "Miembro", "Subdirector", "Subdirectora", "Administrador"}) {
            var authentication = new UsernamePasswordAuthenticationToken("usuario_prueba", "TEST_ONLY_NOT_REAL",
                AuthorityUtils.createAuthorityList("ROLE_" + name));
            assertFalse(InstitutionalRoles.operational(authentication));
        }
        assertFalse(InstitutionalRoles.operational((org.springframework.security.core.Authentication) null));
    }
}
