package com.sigaa.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationCatalogIntegrationTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @WithMockUser(authorities = {"ROLE_administrador", "Notas:Ver"})
    void institutionAndDepartmentDirectorsCanChangeWithoutLosingOtherInstitutionData() throws Exception {
        long institutionId = crearInstitucion("Institución prueba directores", "Director inicial");
        String created = mvc.perform(post("/api/organizations/{id}/departamentos", institutionId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombre":"Operaciones", "director":"Ana Pérez", "directorCargo":"Directora de Operaciones", "activo":true}
                    """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long departmentId = json.readTree(created).get("id").asLong();
        mvc.perform(put("/api/organizations/{id}", institutionId).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Institución prueba directores", "director":"Director nuevo", "directorCargo":"Director Ejecutivo"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.director").value("Director nuevo"))
            .andExpect(jsonPath("$.siglas").value("IPD"));
        mvc.perform(put("/api/organizations/{id}/departamentos/{departmentId}", institutionId, departmentId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombre":"Operaciones", "director":"Luis Gómez", "directorCargo":"Director de Operaciones", "activo":false}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(departmentId))
            .andExpect(jsonPath("$.director").value("Luis Gómez"));
        mvc.perform(get("/api/organizations/{id}/departamentos", institutionId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].activo").value(false)).andExpect(jsonPath("$[0].director").value("Luis Gómez"));
        mvc.perform(get("/api/notas/instituciones"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(String.valueOf(institutionId))));
        mvc.perform(patch("/api/organizations/{id}/toggle", institutionId)).andExpect(status().isOk());
        mvc.perform(get("/api/notas/instituciones"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", not(hasItem(String.valueOf(institutionId)))));
        // Más de una institución activa no debe romper la consulta de la organización principal.
        mvc.perform(get("/api/organizations/principal")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "administrador")
    void departmentCannotBeMovedAcrossInstitutionsOrDuplicated() throws Exception {
        long first = crearInstitucion("Institución primera", "Responsable A");
        long second = crearInstitucion("Institución segunda", "Responsable B");
        String body = """
            {"nombre":"Operaciones", "director":"Responsable", "directorCargo":"Director", "activo":true}
            """;
        String created = mvc.perform(post("/api/organizations/{id}/departamentos", first)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long departmentId = json.readTree(created).get("id").asLong();
        mvc.perform(put("/api/organizations/{id}/departamentos/{departmentId}", second, departmentId)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/api/organizations/{id}/departamentos", first)
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("Operaciones", " operaciones ")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/organizations/{id}/departamentos", first)
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("Operaciones", " ")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "Miembro")
    void memberCannotManageInstitutionsOrDepartments() throws Exception {
        mvc.perform(get("/api/organizations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/organizations/1/departamentos")).andExpect(status().isForbidden());
        mvc.perform(post("/api/organizations/1/departamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Operaciones\",\"activo\":true}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/organizations/1/departamentos/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Operaciones\",\"activo\":true}")).andExpect(status().isForbidden());
    }

    private long crearInstitucion(String nombre, String director) throws Exception {
        String created = mvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("nombre", nombre, "siglas", "IPD", "director", director,
                    "directorCargo", "Director General", "activo", true, "estado", "ACTIVO"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }
}
