package com.sigaa;

import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.repository.PermissionRepository;
import com.sigaa.repository.RoleRepository;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin@sigaa.com", roles = {"superadmin", "administrador"})
class RoleAdministrationIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void institutionalRolesAreMarkedAndCannotBeRenamedDeactivatedArchivedOrReset() throws Exception {
        Role superadmin = roleRepository.findByNombre("Superadmin").orElseThrow();
        assertTrue(Boolean.TRUE.equals(superadmin.getSistema()));
        assertTrue(Boolean.TRUE.equals(superadmin.getActivo()));

        mvc.perform(put("/api/roles/{id}", superadmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Superadmin cambiado\",\"descripcion\":\"x\",\"activo\":true}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/roles/{id}", superadmin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Superadmin\",\"descripcion\":\"x\",\"activo\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/roles/{id}", superadmin.getId()))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/roles/{id}/reset-permissions", superadmin.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void dedicatedPermissionUpdatePreservesRoleMetadataAndRejectsUnknownIds() throws Exception {
        Permission permission = permissionRepository.findByActivoTrue().stream().findFirst().orElseThrow();
        Role role = new Role("Temporal " + UUID.randomUUID(), "Descripción que debe conservarse");
        role.setActivo(false);
        role.setSistema(false);
        role = roleRepository.saveAndFlush(role);

        mvc.perform(put("/api/roles/{id}/permissions", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisoIds\":[" + permission.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Descripción que debe conservarse"))
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.permisos.length()").value(1));

        Role updated = roleRepository.findById(role.getId()).orElseThrow();
        assertEquals("Descripción que debe conservarse", updated.getDescripcion());
        assertFalse(Boolean.TRUE.equals(updated.getActivo()));
        assertEquals(1, updated.getPermisos().size());

        mvc.perform(put("/api/roles/{id}/permissions", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisoIds\":[999999999]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivingCustomRolePreservesTheRecord() throws Exception {
        Role role = new Role("Archivable " + UUID.randomUUID(), "Se conserva para auditoría");
        role.setActivo(true);
        role.setSistema(false);
        role.setPermisos(new HashSet<>());
        role = roleRepository.saveAndFlush(role);

        mvc.perform(delete("/api/roles/{id}", role.getId()))
                .andExpect(status().isNoContent());

        Role archived = roleRepository.findById(role.getId()).orElse(null);
        assertNotNull(archived);
        assertFalse(Boolean.TRUE.equals(archived.getActivo()));
    }

    @Test
    void roleValidationRejectsBlankAndDuplicateNames() throws Exception {
        mvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \",\"descripcion\":\"inválido\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" SUPERADMIN \",\"descripcion\":\"duplicado\"}"))
                .andExpect(status().isBadRequest());
    }
}
